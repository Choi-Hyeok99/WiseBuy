import http from 'k6/http';
import { Counter } from 'k6/metrics';

// 실제 주문 플로우(POST /orders, order-service 직접)로 재고 동시성 정합성을 검증한다.
// Redis Lua 원자적 차감(reserveStock) -> Kafka order.create -> 배치 컨슈머의 DB 반영까지
// 실제 경로를 그대로 태운다. 게이트웨이 레이트리밋(20/s+burst40/유저)을 피하려고 order-service(8084)에 직접 붙는다.
// 요청 바디는 서버가 실제로 안 씀(OrderService.createOrder는 X-Claim-* 헤더와 유저 위시리스트만 본다.
// OrderRequestDto에 @Valid가 안 걸려 있어 검증 자체가 안 탐) - 그래도 형식은 맞춰 보낸다.
const BASE = __ENV.BASE_URL || 'http://localhost:8084';
const USER_FROM = Number(__ENV.USER_FROM || 301);
const USER_TO = Number(__ENV.USER_TO || 310);
const REQS = Number(__ENV.REQS || 800);
const VUS = Number(__ENV.VUS || 100);

const success = new Counter('order_success');       // 200 (재고 예약 성공)
const stockRejected = new Counter('order_stock_rejected'); // 409 (재고 부족 - 정상 거절)
const otherFail = new Counter('order_other_fail');   // 5xx 등 - 진짜 실패

export const options = {
  scenarios: {
    burst: {
      executor: 'shared-iterations',
      vus: VUS,
      iterations: REQS,
      maxDuration: '10m',
    },
  },
};

function pickUser() {
  return USER_FROM + Math.floor(Math.random() * (USER_TO - USER_FROM + 1));
}

export default function () {
  const uid = String(pickUser());
  const headers = { 'Content-Type': 'application/json', 'X-Claim-sub': uid, 'X-Claim-address': 'Seoul' };
  const res = http.post(
    `${BASE}/orders`,
    JSON.stringify({ items: [], shippingAddress: 'Seoul' }),
    { headers, responseCallback: http.expectedStatuses(200, 400, 409, 500, 503) },
  );
  if (res.status === 200) success.add(1);
  else if (res.status === 409) stockRejected.add(1);
  else otherFail.add(1);
}
