#!/bin/bash
# 부하테스트 사전 준비: 테스트 상품 1개 + 유저 위시리스트 시딩.
# 전체 스택이 떠 있어야 한다 (docker compose up).
set -e

PRODUCT_ID="${PRODUCT_ID:-35}"
STOCK="${STOCK:-1000000}"
USER_FROM="${USER_FROM:-301}"
USER_TO="${USER_TO:-310}"
PRODUCT_URL="${PRODUCT_URL:-http://localhost:8082}"
WISHLIST_URL="${WISHLIST_URL:-http://localhost:8083}"

echo "[1/3] 상품 $PRODUCT_ID 확인/생성"
if ! curl -sf "$PRODUCT_URL/products/$PRODUCT_ID" >/dev/null 2>&1; then
  curl -s -X POST "$PRODUCT_URL/products" -H 'Content-Type: application/json' -H 'X-Claim-sub: 1' \
    -d "{\"productName\":\"부하테스트상품\",\"stock\":$STOCK,\"price\":10000,\"description\":\"loadtest\",\"imagePath\":\"/img/x.jpg\",\"status\":\"AVAILABLE\",\"productType\":\"GENERAL\"}" >/dev/null
fi

echo "[2/3] 재고를 $STOCK 로 리셋 (Redis + DB)"
docker exec haengye_redis redis-cli set "product_stock:$PRODUCT_ID" "$STOCK" >/dev/null
docker exec microservices_db mysql -uroot -p"${MYSQL_ROOT_PASSWORD:-b12345678}" \
  -e "UPDATE product_schema.product SET stock=$STOCK WHERE product_id=$PRODUCT_ID;" 2>/dev/null || true

echo "[3/3] 유저 $USER_FROM~$USER_TO 위시리스트에 상품 $PRODUCT_ID 담기"
for uid in $(seq "$USER_FROM" "$USER_TO"); do
  code=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$WISHLIST_URL/wishlist" \
    -H 'Content-Type: application/json' -H "X-Claim-sub: $uid" \
    -d "{\"productId\":$PRODUCT_ID,\"quantity\":1}")
  printf "  u%s:%s " "$uid" "$code"
done
echo
echo "완료. 이제 k6 run 하면 된다."
