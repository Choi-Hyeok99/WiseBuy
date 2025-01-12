import http from 'k6/http';
import { check, sleep } from 'k6';

// 10개의 서로 다른 userId를 미리 정의합니다.
const userIds = [1, 2, 3, 4, 5, 6, 7, 8, 9, 10];  // 숫자형 userId로 처리
const productId = 1;  // 예시로 사용될 상품 ID
const address = "서울시 강남구";  // 예시 주소

// JWT 토큰을 Authorization 헤더에 포함시켜 인증 처리
const jwtToken = 'eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxIiwiZW1haWwiOiJndXIwNzA5QG5hdmVyLmNvbSIsImFkZHJlc3MiOiIxMjMgTWFpbiBTdHJlZXQiLCJpYXQiOjE3MzY0OTEyMzAsImV4cCI6MTczNjU3NzYzMH0.PZwTA2CChUmOcgn6kRMaHypPo0CDGgRDCiOFrZpZYds';  // JWT 토큰

// 주문 생성에 필요한 데이터를 생성
function createOrderData(userId) {
    return {
        userId: userId,  // 숫자형 userId
        productId: productId,
        quantity: 1,
        address: address,
    };
}

// 테스트 옵션 설정
export let options = {
    scenarios: {
        unique_users: {
            executor: 'per-vu-iterations', // 각 유저가 정해진 횟수만큼만 실행
            vus: 500,                     // 1000명의 사용자
            iterations: 1,                 // 각 사용자가 1번씩만 호출
            maxDuration: '1m',             // 최대 테스트 시간
        },
    },
    thresholds: {
        http_req_failed: ['rate<1'],      // 실패율 1% 미만
        http_req_duration: ['p(95)<10000'] // 95%의 요청이 10초 미만이어야 함
    },
};

export default function () {
    // 1. 각 가상 유저가 고유한 userId로 주문을 생성하고 결제 준비를 합니다.
    let userId = userIds[Math.floor(Math.random() * userIds.length)]; // 랜덤으로 userId 선택

    // 1. 주문 생성 API 호출
    let createOrderUrl = 'http://localhost:8000/order-service/orders';
    let orderData = createOrderData(userId);
    let createOrderResponse = http.post(createOrderUrl, JSON.stringify(orderData), {
        headers: {
            'Content-Type': 'application/json',
            'Authorization': `Bearer ${jwtToken}`,
            'X-Claim-sub': userId.toString(),
            'X-Claim-address': address,
        },
    });

    check(createOrderResponse, {
        'order creation is status 200': (r) => r.status === 200,
    });

    if (createOrderResponse.status !== 200) {
        console.error('Order creation failed', createOrderResponse.body);
        return; // 실패 시 테스트 종료
    }

    // 2. 결제 준비 API 호출
    let orderId = createOrderResponse.json().orderId;  // createOrderResponse에서 orderId 추출
    let preparePaymentUrl = `http://localhost:8000/order-service/orders/${orderId}/prepare-payment`;
    let preparePaymentResponse = http.patch(preparePaymentUrl, null, {
        headers: {
            'Authorization': `Bearer ${jwtToken}`,
            'X-Claim-sub': userId.toString(),
            'X-Claim-address': address,
        },
    });

    check(preparePaymentResponse, {
        'payment preparation is status 200': (r) => r.status === 200,
    });

    if (preparePaymentResponse.status !== 200) {
        console.error('Payment preparation failed', preparePaymentResponse.body);
    }

    sleep(1);  // 1초 대기 후 다음 유저로 넘어갑니다
}