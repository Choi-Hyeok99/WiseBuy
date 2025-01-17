import http from 'k6/http';
import { check, sleep } from 'k6';

// 10개의 서로 다른 userId를 미리 정의합니다.
const userIds = [1, 2, 3, 4, 5, 6, 7, 8, 9, 10];
const productId = 13;  // 예시로 사용될 상품 ID
const address = "서울시 강남구";  // 예시 주소

// JWT 토큰
const jwtToken = 'eyJ0eXAiOiJKV1QiLCJhbGciOiJIUzI1NiJ9.eyJzdWIiOiIxIiwiZW1haWwiOiJndXIwNzA5QG5hdmVyLmNvbSIsImFkZHJlc3MiOiIxMjMgTWFpbiBTdHJlZXQiLCJpYXQiOjE3MzcxMDk0MDksImV4cCI6MTczNzE5NTgwOX0.i7hF52rCalK6uWFixkt6tBfoOB_MMQYw0sP0DeDFWPc';

function createOrderData(userId) {
    return {
        userId: userId,
        productId: productId,
        quantity: 1,
        address: address,
    };
}

export let options = {
    scenarios: {
        unique_users: {
            executor: 'per-vu-iterations',
            vus: 300,
            iterations: 1,
            maxDuration: '1m',
        },
    },
    thresholds: {
        http_req_failed: ['rate<1'],
        http_req_duration: ['p(95)<10000']
    },
};

export default function () {
    let userId = userIds[Math.floor(Math.random() * userIds.length)];

    // 1. 주문 생성
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

    // 2. 결제 준비 API 호출
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
        return;
    }

    // 3. 결제 상태 업데이트 API 호출
    let isPaymentSuccessful = Math.random() >= 0.2; // 80% 성공, 20% 실패
    let updateOrderStatusUrl = `http://localhost:8000/order-service/orders/${orderId}/update-status?isPaymentSuccessful=${isPaymentSuccessful}`;
    let updateOrderStatusResponse = http.post(updateOrderStatusUrl, null, {
        headers: {
            'Authorization': `Bearer ${jwtToken}`,
            'X-Claim-sub': userId.toString(),
        },
    });

    check(updateOrderStatusResponse, {
        'order status update is status 200': (r) => r.status === 200 || r.status === 400, // 실패도 허용
    });

    // 로그 출력으로 성공/실패 확인
    console.log(`Order ID: ${orderId}, Payment Successful: ${isPaymentSuccessful}`);

    if (updateOrderStatusResponse.status !== 200) {
        console.error('Order status update failed', updateOrderStatusResponse.body);
    }

    sleep(0.1); // 1초 대기 후 다음 유저로 넘어감
}