# 부하테스트 (k6)

## 목적

"목표 부하를 찍고 성공했다"가 아니라 **"초당 요청 수(RPS)를 점진적으로 올려서 어디서 깨지는지, 그때 병목이 무엇인지"** 를 측정한다.

- `ramping-arrival-rate` 사용: 목표 RPS를 정하면 k6가 VU를 알아서 조절한다(도착률 기반). VU 수 자체는 부하생성기 설정일 뿐이라 "동시 사용자 N명"이라고 말하지 않는다.
- **409(재고 소진 정상 거절)는 실패로 세지 않는다.** `http_req_failed`에는 5xx·네트워크 오류만 잡힌다.

## 사전 준비

```bash
docker compose up -d          # 전체 스택
bash load-test/seed.sh        # 테스트 상품 + 유저 위시리스트 시딩
```

## 실행

```bash
# 재고 조회 (Gateway 경유, ramping-arrival-rate 연속 램프)
k6 run load-test/stock-read.js

# 재고 조회 순수 성능 — Gateway/Rate Limiter 배제, Product Service(:8082) 직접 호출
# 목표 RPS 하나를 고정해서 받는다. 여러 RPS 단계를 점진적으로 보려면 RATE를 올려가며 반복 실행.
k6 run --env RATE=500 --env DURATION=60s load-test/product-stock-progressive.js

# 주문 → 결제 플로우 (재고를 크게 잡아야 sustained 측정 가능)
STOCK=1000000 bash load-test/seed.sh
k6 run load-test/order-flow.js

# 주문 동시성(오버셀 검증) — 재고를 작게 고정하고 그보다 많은 주문을 동시에 발생시켜 오버셀 여부 확인
k6 run load-test/order-concurrency.js
```

환경변수로 조정: `BASE_URL`, `GATEWAY_URL`, `PAYMENT_URL`, `PRODUCT_ID`, `USER_FROM`, `USER_TO`. `product-stock-progressive.js`는 `RATE`/`DURATION`/`PREALLOC`/`MAXVUS`.

## 결과 읽는 법

| 지표 | 의미 |
|---|---|
| `http_reqs .../s` | 실제 처리량 (TPS) |
| `http_req_duration p(95)` | 95%가 이 시간 안에 응답 |
| `http_req_failed` | 5xx·네트워크 오류 비율 (409 제외) |
| `orders_rejected_by_stock` | 재고 소진으로 정상 거절된 주문 수 |

**한계 지점** = 실패율이 튀거나 p95가 급증하기 시작하는 RPS 구간. 그 구간에서 `docker stats`, MySQL 슬로우 로그, Kafka consumer lag 을 같이 봐서 병목을 특정한다.

## 측정 환경 (중요)

- 로컬 단일 머신, 부하생성기(k6)와 앱 스택이 **같은 호스트**에 있음 → 절대 수치는 이 환경 한정.
- 컨테이너 11개 + k6가 같은 CPU를 나눠 쓰므로, 리소스 경합이 병목으로 나올 수 있음.
- 다른 프로그램은 닫고 측정할 것.

## 재고 원자성 검증 (`lua-concurrency-check.sh`)

`docker compose up -d redis` 후 실행. RedisUtility의 재고 차감 Lua 스크립트를 redis-cli로
동시에 800번 실행해 오버셀(재고 음수)이 없는지 확인한다. gradle/JVM 불필요.

```
$ bash load-test/lua-concurrency-check.sh
재고 100, 동시 요청 800건 → 성공 100 / 거절 700 / 최종 재고 0
== PASS: 오버셀 없음 ==
```

같은 검증의 JUnit 버전은 `product/.../ProductStockConcurrencyTest.java` (localhost:6379 Redis 필요).

## SAGA 보상 정합성 검증 (`saga-consistency-check.sh`)

결제 실패 / 결제 이탈(만료) / 다중 상품 부분 실패가 섞인 부하를 준 뒤, 모든 보상이 끝나면
**Redis 재고 = DB 재고 = 초기 재고 − 결제 완료 수량**인지 확인한다. 상품·유저는 실행마다 새로 만든다.

```bash
# 결제 20% 실패, 결제 대기 만료를 1분으로 줄여서 기동
PAYMENT_FAILURE_RATE=0.2 ORDER_PAYMENT_TIMEOUT_MINUTES=1 ORDER_EXPIRATION_CHECK_INTERVAL_MS=10000 docker compose up -d
bash load-test/saga-consistency-check.sh      # STOCK, RATE, DURATION, ABANDON_RATE 조정 가능
```

```
주문 PAYMENT_COMPLETED 259 / PAYMENT_FAILED 74 / EXPIRED 41, 부분 실패 181건 전부 롤백
기대 재고 300 - 259 = 41  →  Redis 41 / DB 41
== PASS: 보상 후 Redis = DB = 초기 재고 - 결제 완료 수량 ==
```

- 부분 실패는 상품 B의 DB 재고는 넉넉하게, Redis 예약 카운터는 0으로 둬서 "사전 검증은 통과했는데 예약 직전에 다른 사람이 마지막 재고를 가져간" 상황을 결정적으로 재현한다.
- 시작 전에 Kafka 컨슈머 lag이 0이 될 때까지 기다린다. 이전 작업의 보상 이벤트가 소비 중이면 DB가 바빠 측정이 오염된다(실제로 한 번 겪음).

