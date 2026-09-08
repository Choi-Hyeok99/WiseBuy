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
# 재고 조회
k6 run load-test/stock-read.js

# 주문 → 결제 플로우 (재고를 크게 잡아야 sustained 측정 가능)
STOCK=1000000 bash load-test/seed.sh
k6 run load-test/order-flow.js
```

환경변수로 조정: `BASE_URL`, `GATEWAY_URL`, `PAYMENT_URL`, `PRODUCT_ID`, `USER_FROM`, `USER_TO`.

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
