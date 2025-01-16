import http from 'k6/http';
import { check, sleep } from 'k6';

export let options = {
    stages: [
        { duration: '1m', target: 1000 }, // 1분 동안 1,000명까지 증가
        { duration: '10s', target: 0 },     // 10초 동안 종료
    ],
};

export default function () {
    let res = http.get('http://localhost:8000/product-service/products/5/stock'); // 재고 조회 API 호출
    check(res, {
        'status is 200': (r) => r.status === 200, // 상태 코드가 200인지 확인
        'response time < 1000ms': (r) => r.timings.duration < 1000, // 응답 시간이 1000ms 이하인지 확인
    });
    sleep(0.2); // 1초 대기
}