import http from 'k6/http';
import { check } from 'k6';
import { Counter } from 'k6/metrics';

// SAGA 보상 검증용 부하. saga-consistency-check.sh가 상품/위시리스트를 준비한 뒤 실행한다 (단독 실행 X).
//
//  normal  : 위시리스트 [A]. 주문 → 결제(payment 실패율은 PAYMENT_FAILURE_RATE로 설정) / ABANDON_RATE 비율은 결제 안 하고 이탈
//  partial : 위시리스트 [A, B]. B는 검증(DB 재고)은 통과하지만 Redis 예약에서 409 → A 예약이 롤백 보상돼야 함
//
// 보상 경로 네 가지(결제 실패 / 미결제 만료 / 부분 실패 롤백 / 재전달 멱등)가 한 번에 섞여 돌고,
// 끝난 뒤 스크립트가 "Redis 재고 = DB 재고 = 초기 재고 - 결제 완료 수량"인지 확인한다.
const GW = __ENV.GATEWAY_URL || 'http://localhost:8000';
const PAY = __ENV.PAYMENT_URL || 'http://localhost:8085';
const BASE = Number(__ENV.UID_BASE);
const ABANDON_RATE = Number(__ENV.ABANDON_RATE || 0.1);
const RATE = Number(__ENV.RATE || 20);
const DURATION = __ENV.DURATION || '60s';

const ordered = new Counter('saga_orders_created');
const abandoned = new Counter('saga_orders_abandoned');
const paid = new Counter('saga_payments_sent');
const soldOut = new Counter('saga_orders_sold_out');
const partialRejected = new Counter('saga_partial_rejected');
const partialUnexpected = new Counter('saga_partial_unexpected');

export const options = {
  scenarios: {
    normal: {
      executor: 'constant-arrival-rate', rate: RATE, timeUnit: '1s', duration: DURATION,
      preAllocatedVUs: 50, maxVUs: 300, exec: 'normal',
    },
    partial: {
      executor: 'constant-arrival-rate', rate: Math.max(1, Math.round(RATE / 6)), timeUnit: '1s', duration: DURATION,
      preAllocatedVUs: 10, maxVUs: 60, exec: 'partial',
    },
  },
};

function headers(uid) {
  return { 'Content-Type': 'application/json', 'X-Claim-sub': String(uid), 'X-Claim-address': 'Seoul' };
}

function order(uid) {
  return http.post(`${GW}/order-service/orders`, JSON.stringify({ address: 'Seoul' }),
    { headers: headers(uid), tags: { name: 'order' }, responseCallback: http.expectedStatuses(200, 409) });
}

export function normal() {
  const uid = BASE + 1 + Math.floor(Math.random() * 40);
  const res = order(uid);
  if (res.status === 409) { soldOut.add(1); return; }
  if (!check(res, { 'order 200': (r) => r.status === 200 })) return;
  ordered.add(1);

  if (Math.random() < ABANDON_RATE) { abandoned.add(1); return; } // 결제창 이탈 → 만료 스케줄러가 회수해야 함

  const body = res.json();
  const payRes = http.post(`${PAY}/payments`,
    JSON.stringify({ orderId: body.orderId, userId: uid, totalAmount: body.totalAmount }),
    { headers: headers(uid), tags: { name: 'payment' } });
  if (check(payRes, { 'payment 200': (r) => r.status === 200 })) paid.add(1);
}

export function partial() {
  const uid = BASE + 51 + Math.floor(Math.random() * 10);
  const res = order(uid);
  if (res.status === 409) partialRejected.add(1);
  else partialUnexpected.add(1);
}
