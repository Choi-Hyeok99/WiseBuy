#!/bin/bash
# RedisUtility의 STOCK_UPDATE_SCRIPT를 그대로, redis-cli로 동시에 N번 실행해서
# 오버셀(재고 음수)이 나는지 검증한다. gradle/JVM 없이 순수 Redis 원자성 확인.
set -u
R() { docker exec haengye_redis redis-cli "$@"; }

KEY="product_stock:cctest"
LUA="local stock = redis.call('get', KEYS[1]) if not stock then return -1 end stock = tonumber(stock) if stock < tonumber(ARGV[1]) then return -2 end redis.call('decrby', KEYS[1], ARGV[1]) return stock - ARGV[1]"

INIT=100
REQS=800

R set "$KEY" "$INIT" >/dev/null
echo "초기 재고: $(R get "$KEY"), 동시 요청: $REQS"

TMP=$(mktemp -d)
# 800개를 백그라운드로 동시에 (50개씩 배치로 나눠 fork storm 방지)
i=0
while [ $i -lt $REQS ]; do
  for _ in $(seq 1 50); do
    [ $i -lt $REQS ] || break
    ( docker exec haengye_redis redis-cli eval "$LUA" 1 "$KEY" 1 > "$TMP/r$i" 2>&1 ) &
    i=$((i+1))
  done
  wait
done

SUCCESS=$(grep -lvE '^-2$|^-1$' "$TMP"/r* 2>/dev/null | wc -l | tr -d ' ')
REJECT=$(grep -lE '^-2$' "$TMP"/r* 2>/dev/null | wc -l | tr -d ' ')
FINAL=$(R get "$KEY")

echo "-----"
echo "성공(>=0 반환): $SUCCESS   (기대: $INIT)"
echo "거절(-2 반환) : $REJECT   (기대: $((REQS - INIT)))"
echo "최종 재고     : $FINAL   (기대: 0, 절대 음수 아님)"
echo "-----"
PASS=1
[ "$SUCCESS" = "$INIT" ] || { echo "FAIL: 성공 건수 불일치"; PASS=0; }
[ "$REJECT" = "$((REQS - INIT))" ] || { echo "FAIL: 거절 건수 불일치"; PASS=0; }
[ "$FINAL" = "0" ] || { echo "FAIL: 최종 재고 $FINAL"; PASS=0; }
[ "$FINAL" -ge 0 ] 2>/dev/null || { echo "FAIL: 재고 음수!"; PASS=0; }
R del "$KEY" >/dev/null
rm -rf "$TMP"
[ $PASS = 1 ] && echo "== PASS: 오버셀 없음, 정확히 재고만큼만 성공 ==" || echo "== FAIL =="
