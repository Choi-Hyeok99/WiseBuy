package com.sparta.order.controller;

import com.sparta.common.dto.OrderResponseForPaymentDto;
import com.sparta.order.dto.OrderRequestDto;
import com.sparta.order.dto.OrderResponseDto;
import com.sparta.order.dto.UpdateOrderStatusRequest;
import com.sparta.order.service.OrderService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;


@RestController
@RequestMapping("/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderService orderService;

    // 1. 주문 생성
    @PostMapping
    public ResponseEntity<OrderResponseDto> createOrders(@RequestBody OrderRequestDto requestDto, HttpServletRequest request){


        Long userId = Long.parseLong(request.getHeader("X-Claim-sub")); // id 값 들어감
        String address = request.getHeader("X-Claim-address"); // 주소 값 가져오기

        // 주소가 없으면 예외 처리
        if (address == null || address.isEmpty()) {
            throw new IllegalArgumentException("주소가 비어 있습니다.");
        }

        OrderResponseDto responseDto = orderService.createOrder(requestDto, userId, address);
       return ResponseEntity.ok(responseDto);
    }

    // 2. 주문 조회
    @GetMapping
    public ResponseEntity<List<OrderResponseDto>> getOrders(HttpServletRequest request){
        // 유저 정보 가져오기 (헤더에서 사용자 ID를 가져오는 방식)
        Long userId = Long.parseLong(request.getHeader("X-Claim-sub"));  // 헤더에서 userId 가져오기

        // 주문 조회
        List<OrderResponseDto> orders = orderService.getOrders(userId);

        return ResponseEntity.ok(orders);
    }
    // 3. 주문 취소
    @DeleteMapping("/{orderId}")
    public ResponseEntity<String> cancelOrder(@PathVariable Long orderId, HttpServletRequest request) {
        // 유저 정보 가져오기 (헤더에서 사용자 ID를 가져오는 방식)
        Long userId = Long.parseLong(request.getHeader("X-Claim-sub"));  // 헤더에서 userId 가져오기

        // 주문 취소 처리
        orderService.cancelOrder(orderId, userId);
        return ResponseEntity.ok("주문이 취소되었습니다.");
    }

    // 4. 반품 요청
    @PatchMapping("/{orderId}/return")
    public ResponseEntity<String> returnOrder(@PathVariable Long orderId,HttpServletRequest request) {

        Long userId = Long.parseLong(request.getHeader("X-Claim-sub"));  // 헤더에서 userId 가져오기


        // 반품 요청 처리
        orderService.returnOrder(orderId, userId);
        return ResponseEntity.ok("주문 반품 요청이 접수되었습니다.");
    }
    // 5. 단건 주문 조회
    @GetMapping("/{orderId}")
    public ResponseEntity<OrderResponseForPaymentDto> getOrderById(@PathVariable("orderId") Long id,@RequestParam Long userId) {
        OrderResponseForPaymentDto order = orderService.getOrderById(id, userId);
        return ResponseEntity.ok(order);
    }
    // 6.주문 결제 진입 조회 API
    @PatchMapping("/{orderId}/prepare-payment")
    public ResponseEntity<String> preparePayment(@PathVariable Long orderId, HttpServletRequest request){
        Long userId = Long.parseLong(request.getHeader("X-Claim-sub"));
        orderService.preparePayment(orderId,userId);
        return ResponseEntity.ok("결제 준비가 완료되었습니다.");
    }
    // 결제 상태 업데이트 API
    @PostMapping("/{orderId}/update-status")
    public ResponseEntity<String> updateOrderStatus(
            @PathVariable Long orderId,
            @RequestParam boolean isPaymentSuccessful,
            @RequestHeader("X-Claim-sub") Long userId) { // 헤더에서 userId 받아오기
        orderService.updateOrderStatusAfterPayment(orderId, isPaymentSuccessful, userId);
        return ResponseEntity.ok("주문 상태가 업데이트되었습니다.");
    }
}
