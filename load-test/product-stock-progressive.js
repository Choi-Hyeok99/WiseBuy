import http from 'k6/http';
import { check } from 'k6';
import { Counter } from 'k6/metrics';

// 인기 상품 1개 재고 조회(hot-key) 순수 성능 측정용.
// Gateway(8000)를 거치지 않고 Product Service(8082) 컨테이너 포트를 직접 호출해
// Gateway/RateLimiter 영향을 배제한다.
// 하나의 프로세스에서 목표 RPS를 계속 바꾸며 단일 ramping-arrival-rate로 돌리면
// 단계 경계에서 값이 선형으로 섞여(예: 300->600 구간 평균이 450) "300 RPS 단계"의
// 순수한 실제 수치를 얻기 어렵다. 그래서 이 스크립트는 RATE 하나를 고정해서 받고,
// 오케스트레이션(run-progressive.sh)이 이 스크립트를 RPS 단계별로 순차 실행해
// 각 단계마다 독립된 --summary-export JSON을 얻는다.
const BASE = __ENV.BASE_URL || 'http://localhost:8082';
const PRODUCT_ID = __ENV.PRODUCT_ID || '35';
const RATE = Number(__ENV.RATE || 100);
const DURATION = __ENV.DURATION || '30s';
const PREALLOC = Number(__ENV.PREALLOC || Math.max(50, RATE));
const MAXVUS = Number(__ENV.MAXVUS || Math.max(200, RATE * 3));

const status2xx = new Counter('status_2xx');
const status4xx = new Counter('status_4xx');
const status5xx = new Counter('status_5xx');
const statusOther = new Counter('status_other');

export const options = {
  scenarios: {
    stage: {
      executor: 'constant-arrival-rate',
      rate: RATE,
      timeUnit: '1s',
      duration: DURATION,
      preAllocatedVUs: PREALLOC,
      maxVUs: MAXVUS,
    },
  },
  summaryTrendStats: ['avg', 'min', 'med', 'max', 'p(90)', 'p(95)', 'p(99)'],
};

export default function () {
  const res = http.get(`${BASE}/products/${PRODUCT_ID}/stock`);
  check(res, { 'status is 200': (r) => r.status === 200 });

  if (res.status >= 200 && res.status < 300) status2xx.add(1);
  else if (res.status >= 400 && res.status < 500) status4xx.add(1);
  else if (res.status >= 500) status5xx.add(1);
  else statusOther.add(1);
}
