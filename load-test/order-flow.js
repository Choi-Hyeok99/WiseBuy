import http from 'k6/http';
import { check } from 'k6';
import { Counter } from 'k6/metrics';

// 주문 → 결제 플로우 부하테스트.
// 주문은 "요청 본문의 상품"이 아니라 "해당 유저의 위시리스트"로 구성되므로, seed.sh로 유저 위시리스트를
// 먼저 채워둬야 한다. 게이트웨이가 Authorization 헤더가 없으면 X-Claim-* 를 통과시키므로 JWT 없이 테스트.
const GW = __ENV.GATEWAY_URL || 'http://localhost:8000';
const PAY = __ENV.PAYMENT_URL || 'http://localhost:8085';
const USER_FROM = Number(__ENV.USER_FROM || 301);
const USER_TO = Number(__ENV.USER_TO || 310);

// 409 = 재고 소진, 429 = 게이트웨이 rate limit. 둘 다 "시스템이 의도적으로 거절한 것"이라
// 5xx(진짜 장애)와 구분해서 따로 센다.
const rejectedByStock = new Counter('orders_rejected_by_stock');
const rejectedByRateLimit = new Counter('orders_rejected_by_ratelimit');

export const options = {
  scenarios: {
    ramp: {
      executor: 'ramping-arrival-rate',
      startRate: 20,
      timeUnit: '1s',
      preAllocatedVUs: 100,
      maxVUs: 1500,
      stages: [
        { target: 20,  duration: '20s' }, // warmup
        { target: 100, duration: '30s' },
        { target: 300, duration: '30s' },
        { target: 600, duration: '30s' },
        { target: 900, duration: '30s' },
        { target: 0,   duration: '10s' },
      ],
    },
  },
  thresholds: {
    // 409/429는 expectedStatuses로 제외했으므로 http_req_failed에는 5xx/네트워크 오류만 잡힌다.
    http_req_failed: ['rate<0.02'],
    'http_req_duration{name:order}': ['p(95)<1500'],
  },
};

function pickUser() {
  return USER_FROM + Math.floor(Math.random() * (USER_TO - USER_FROM + 1));
}

export default function () {
  const uid = String(pickUser());
  const headers = { 'Content-Type': 'application/json', 'X-Claim-sub': uid, 'X-Claim-address': 'Seoul' };

  const orderRes = http.post(
    `${GW}/order-service/orders`,
    JSON.stringify({ userId: Number(uid), quantity: 1, address: 'Seoul' }),
    { headers, tags: { name: 'order' }, responseCallback: http.expectedStatuses(200, 409, 429) },
  );

  if (orderRes.status === 429) {
    rejectedByRateLimit.add(1); // 게이트웨이 rate limit = 의도된 거절
    return;
  }
  if (orderRes.status === 409) {
    rejectedByStock.add(1); // 재고 소진 = 의도된 거절
    return;
  }
  if (!check(orderRes, { 'order is 200': (r) => r.status === 200 })) return;

  const body = orderRes.json();
  const payRes = http.post(
    `${PAY}/payments`,
    JSON.stringify({ orderId: body.orderId, userId: Number(uid), totalAmount: body.totalAmount }),
    { headers, tags: { name: 'payment' } },
  );
  check(payRes, { 'payment is 200': (r) => r.status === 200 });
}
