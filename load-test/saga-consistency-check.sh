#!/bin/bash
# SAGA 보상 정합성 검증: 보상 경로가 섞인 부하 후 재고가 정확히 맞는지 확인한다.
#
#  - 결제 실패(PAYMENT_FAILURE_RATE)  → stock.rollback (Redis + DB 복구)
#  - 결제 이탈(ABANDON_RATE)          → 만료 스케줄러 → stock.rollback (Redis + DB 복구)
#  - 다중 상품 부분 실패(A 성공, B 409) → 주문 롤백 → stock.rollback restoreDb=false (Redis만 복구)
#
# 통과 조건 (모든 주문이 끝난 뒤):
#   Redis 재고(A) = DB 재고(A) = 초기 재고 - 결제 완료된 A 수량
#   결제 대기(PENDING/PAYMENT_PENDING)로 남은 주문 0건, 부분 실패 유저의 주문 0건
#
# 사전 조건: 스택이 아래 설정으로 떠 있어야 한다 (만료를 1분으로 줄이고 결제 20% 실패).
#   PAYMENT_FAILURE_RATE=0.2 ORDER_PAYMENT_TIMEOUT_MINUTES=1 ORDER_EXPIRATION_CHECK_INTERVAL_MS=10000 docker compose up -d
set -euo pipefail

STOCK="${STOCK:-300}"
RATE="${RATE:-20}"
DURATION="${DURATION:-60s}"
ABANDON_RATE="${ABANDON_RATE:-0.1}"
WAIT_TIMEOUT="${WAIT_TIMEOUT:-900}"   # 보상이 다 끝날 때까지 기다리는 최대 시간(초)
PRODUCT_URL="${PRODUCT_URL:-http://localhost:8082}"
WISHLIST_URL="${WISHLIST_URL:-http://localhost:8083}"
MYSQL_PW="${MYSQL_ROOT_PASSWORD:-$(grep ^MYSQL_ROOT_PASSWORD .env | cut -d= -f2)}"
RUN=$(date +%s)
UID_BASE=$(( (RUN % 1000000) * 100 ))   # 실행마다 새 유저 범위 (이전 위시리스트와 안 섞이게)

sql() { docker exec microservices_db mysql -uroot -p"$MYSQL_PW" -N -B -e "$1" 2>/dev/null; }
redis() { docker exec haengye_redis redis-cli "$@"; }

create_product() {
  curl -sf -X POST "$PRODUCT_URL/products" -H 'Content-Type: application/json' -H 'X-Claim-sub: 1' \
    -d "{\"productName\":\"$1\",\"stock\":$2,\"price\":1000,\"description\":\"saga\",\"imagePath\":\"/img/x.jpg\",\"status\":\"AVAILABLE\",\"productType\":\"GENERAL\"}" >/dev/null
  sql "select max(product_id) from product_schema.product where product_name='$1';"
}

add_wish() {
  curl -sf -o /dev/null -X POST "$WISHLIST_URL/wishlist" -H 'Content-Type: application/json' \
    -H "X-Claim-sub: $1" -d "{\"productId\":$2,\"quantity\":1}"
}

kafka_lag() {
  docker exec kafka kafka-consumer-groups --bootstrap-server localhost:9092 --describe --all-groups 2>/dev/null \
    | awk '$6 ~ /^[0-9]+$/ {s+=$6} END {print s+0}'
}

# 이전 작업(만료된 묵은 주문의 보상 이벤트 등)이 아직 소비 중이면 DB가 바빠 측정이 오염된다 → 비워질 때까지 대기
echo "[0/5] Kafka 컨슈머 lag 0 대기"
until [ "$(kafka_lag)" = "0" ]; do echo "  lag $(kafka_lag)"; sleep 5; done

echo "[1/5] 상품 준비"
A=$(create_product "SAGA-A-$RUN" "$STOCK")
B=$(create_product "SAGA-B-$RUN" 1000)
# B: DB 재고는 넉넉해서 order의 사전 검증(getProductById)은 통과하지만 Redis 예약 카운터는 0
#    → "검증과 예약 사이에 다른 사람이 마지막 재고를 가져간" 상황을 결정적으로 재현한다.
redis set "product_stock:$A" "$STOCK" >/dev/null
redis set "product_stock:$B" 0 >/dev/null
echo "  A=$A (재고 $STOCK), B=$B (Redis 0 / DB 1000), 유저 $((UID_BASE+1))~$((UID_BASE+60))"

echo "[2/5] 위시리스트 시딩"
for i in $(seq 1 40); do add_wish $((UID_BASE+i)) "$A"; done
for i in $(seq 51 60); do add_wish $((UID_BASE+i)) "$A"; add_wish $((UID_BASE+i)) "$B"; done

echo "[3/5] 부하 (${RATE} req/s + 부분실패 $((RATE/6)) req/s, $DURATION)"
k6 run --quiet --env UID_BASE="$UID_BASE" --env RATE="$RATE" --env DURATION="$DURATION" \
  --env ABANDON_RATE="$ABANDON_RATE" load-test/saga-flow.js 2>&1 | grep -E "saga_|http_req_failed|checks" || true

USERS="user_id between $((UID_BASE+1)) and $((UID_BASE+60))"
echo "[4/5] 보상 완료 대기 (결제 대기 주문 0건 + Kafka lag 0 + Redis/DB 재고 안정화, 최대 ${WAIT_TIMEOUT}s)"
start=$(date +%s); prev=""
while true; do
  pending=$(sql "select count(*) from order_schema.orders where $USERS and status in ('PENDING','PAYMENT_PENDING');")
  snap="$(redis get "product_stock:$A")/$(sql "select stock from product_schema.product where product_id=$A;")"
  lag=$(kafka_lag)
  echo "  +$(( $(date +%s) - start ))s 결제대기 ${pending}건, Kafka lag ${lag}, Redis/DB 재고 ${snap}"
  if [ "$pending" = "0" ] && [ "$lag" = "0" ] && [ "$snap" = "$prev" ]; then break; fi
  if [ $(( $(date +%s) - start )) -gt "$WAIT_TIMEOUT" ]; then echo "  타임아웃"; break; fi
  prev="$snap"; sleep 10
done

echo "[5/5] 결과"
sql "select status, count(*) from order_schema.orders where $USERS group by status;" | sed 's/^/  주문 /'
sold=$(sql "select coalesce(sum(i.quantity),0) from order_schema.order_item i join order_schema.orders o on o.id=i.order_id
            where o.$USERS and i.product_id=$A and o.status not in ('PAYMENT_FAILED','EXPIRED','CANCELLED');")
partial_orders=$(sql "select count(*) from order_schema.orders where user_id between $((UID_BASE+51)) and $((UID_BASE+60));")
redis_a=$(redis get "product_stock:$A"); db_a=$(sql "select stock from product_schema.product where product_id=$A;")
redis_b=$(redis get "product_stock:$B")
expected=$(( STOCK - sold ))

echo "  결제 완료 수량(A)  : $sold"
echo "  기대 재고(A)       : $STOCK - $sold = $expected"
echo "  Redis 재고(A)      : $redis_a"
echo "  DB 재고(A)         : $db_a"
echo "  Redis 재고(B)      : $redis_b (0이어야 함 - 예약된 적 없음)"
echo "  부분 실패 유저 주문 : $partial_orders (0이어야 함 - 전부 롤백)"

if [ "$redis_a" = "$expected" ] && [ "$db_a" = "$expected" ] && [ "$redis_b" = "0" ] && [ "$partial_orders" = "0" ]; then
  echo "== PASS: 보상 후 Redis = DB = 초기 재고 - 결제 완료 수량 =="
else
  echo "== FAIL =="; exit 1
fi
