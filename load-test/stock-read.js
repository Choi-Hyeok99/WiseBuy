import http from 'k6/http';
import { check } from 'k6';

// 재고 조회 API 부하테스트.
// "목표 VU를 찍고 성공했다"가 아니라 "초당 요청 수(RPS)를 점진적으로 올려서 어디서 깨지는지" 를 본다.
// ramping-arrival-rate: k6가 목표 RPS를 맞추려고 VU를 알아서 늘린다(도착률 기반).
const BASE = __ENV.BASE_URL || 'http://localhost:8000/product-service';
const PRODUCT_ID = __ENV.PRODUCT_ID || '35';

// RPS 단계. 무릎(p95가 꺾이는 지점)을 보려고 좁게 잡았다. --env MAX_RPS 로 상한 조정 가능.
const MAX = Number(__ENV.MAX_RPS || 1600);
export const options = {
  scenarios: {
    ramp: {
      executor: 'ramping-arrival-rate',
      startRate: 100,
      timeUnit: '1s',
      preAllocatedVUs: 100,
      maxVUs: 2000,
      stages: [
        { target: 100,               duration: '15s' }, // warmup
        { target: Math.round(MAX*0.25), duration: '25s' },
        { target: Math.round(MAX*0.5),  duration: '25s' },
        { target: Math.round(MAX*0.75), duration: '25s' },
        { target: MAX,                  duration: '25s' },
        { target: 0,                    duration: '10s' },
      ],
    },
  },
  thresholds: {
    http_req_failed: ['rate<0.01'],
    http_req_duration: ['p(95)<500', 'p(99)<1000'],
  },
};

export default function () {
  const res = http.get(`${BASE}/products/${PRODUCT_ID}/stock`);
  check(res, { 'status is 200': (r) => r.status === 200 });
}
