import http from 'k6/http';
import { check, sleep } from 'k6';

// 10개의 서로 다른 userId를 미리 정의
const userIds = [1, 2, 3, 4, 5, 6, 7, 8, 9, 10];
const productId = 22;  // 예제 상품 ID
const address = "서울시 강남구";  // 예제 주소

// JWT 토큰 (테스트용, 실제 테스트 시 동적으로 설정 가능)
const jwtToken = 'eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxIiwiZW1haWwiOiJndXIwNzA5QG5hdmVyLmNvbSIsImFkZHJlc3MiOiIxMjMgTWFpbiBTdHJlZXQiLCJpYXQiOjE3NDAzNjQ0OTgsImV4cCI6MTc0MDQ1MDg5OH0.v2BQJYlVIUprPHp0R-hUbVx8QgrP45Uea1b-VXEQa1w';

// K6 부하 테스트 옵션 설정
export let options = {
    scenarios: {
        unique_users: {
            executor: 'per-vu-iterations',
            vus: 200, // 100명의 사용자가 동시에 요청
            iterations: 1,
            maxDuration: '1m',
        },
    },
    thresholds: {
        http_req_failed: ['rate<1'],  // HTTP 요청 실패율 1% 미만
        http_req_duration: ['p(95)<10000']  // 95%의 요청이 10초 이내 완료
    },
};

// 주문 생성 데이터 함수
function createOrderData(userId) {
    return {
        userId: userId,
        productId: productId,
        quantity: 1,
        address: address,
    };
}

// Kafka 기반 주문-결제 프로세스 테스트
export default function () {
    let userId = userIds[Math.floor(Math.random() * userIds.length)];

    // 1️⃣ 주문 생성 (Order Service)
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
        return;
    }

    let orderId = createOrderResponse.json().orderId;

    // 2️⃣ 결제 요청 (Payment Service) -> HTTP 요청
    let paymentUrl = 'http://localhost:8085/payments';
    let paymentData = {
        orderId: orderId,
        userId: userId,
        totalAmount: createOrderResponse.json().totalAmount,
    };

    let paymentResponse = http.post(paymentUrl, JSON.stringify(paymentData), {
        headers: {
            'Content-Type': 'application/json',
            'Authorization': `Bearer ${jwtToken}`,
            'X-Claim-sub': userId.toString(),
        },
    });

    check(paymentResponse, {
        'payment request is status 200': (r) => r.status === 200,
    });

    if (paymentResponse.status !== 200) {
        console.error('Payment request failed', paymentResponse.body);
        return;
    }
}