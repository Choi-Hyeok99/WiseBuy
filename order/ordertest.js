import http from 'k6/http';
import { check, sleep } from 'k6';

// 10개의 서로 다른 userId를 미리 정의
const userIds = [1, 2, 3, 4, 5, 6, 7, 8, 9, 10];
const productId = 32;  // 예제 상품 ID
const address = "서울시 강남구";  // 예제 주소

// JWT 토큰을 코드에 직접 적으면 git에 그대로 남기 때문에(실제 발급받은 토큰 + 이메일이 페이로드에 노출됐었음),
// 실행할 때 k6의 --env 옵션으로 주입받는 방식으로 바꾼다.
// 아래 Authorization 헤더에서 'Bearer '를 이미 붙이므로, 여기엔 "Bearer " 없이 토큰 값만 넣는다.
// 실행 예시: k6 run --env JWT_TOKEN="eyJ..." ordertest.js
const jwtToken = __ENV.JWT_TOKEN;

if (!jwtToken) {
    throw new Error('JWT_TOKEN 환경변수가 필요합니다. 예: k6 run --env JWT_TOKEN="발급받은 토큰" ordertest.js');
}

// K6 부하 테스트 옵션 설정
export let options = {
    scenarios: {
        unique_users: {
            executor: 'per-vu-iterations',
            vus: 1000, // 3000명의 사용자가 동시에 요청
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

    // 주문 생성 (Order Service)
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