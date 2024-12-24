package com.sparta.haengye_project.order.controller;

import com.sparta.haengye_project.order.dto.OrderRequestDto;
import com.sparta.haengye_project.order.dto.OrderResponseDto;
import com.sparta.haengye_project.order.service.OrderService;
import com.sparta.haengye_project.security.UserDetailsImpl;
import com.sparta.haengye_project.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("orders")
@RequiredArgsConstructor
public class OrderController {


    private final OrderService orderService;

    // 1. 주문 생성
    @PostMapping
    public ResponseEntity<OrderResponseDto> createOrders(@RequestBody OrderRequestDto requestDto){

        // 유저 확인하고 이제 주문
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        User user = userDetails.getUser();
        String address = user.getAddress(); // 사용자 주소 가져오기

       OrderResponseDto responseDto =  orderService.createOrder(requestDto,user,address);
       return ResponseEntity.ok(responseDto);
    }

    // 2. 주문 조회
    @GetMapping
    public ResponseEntity<List<OrderResponseDto>> getOrders(){
        // 유저 확인하고 이제 주문
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        User user = userDetails.getUser();

        List<OrderResponseDto> orders = orderService.getOrders(user);
        return ResponseEntity.ok(orders);

    }
    // 3. 주문 취소
    @DeleteMapping("/{orderId}")
    public ResponseEntity<String> cancelOrder(@PathVariable Long orderId){

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        User user = userDetails.getUser();

        orderService.cancelOrder(orderId,user);
        return ResponseEntity.ok("주문이 취소되었습니다. ");
    }

    // 4. 반품 요청
    @PatchMapping("/{orderId}/return")
    public ResponseEntity<String> returnOrder(@PathVariable Long orderId){

        // 유저 확인
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        UserDetailsImpl userDetails = (UserDetailsImpl) authentication.getPrincipal();
        User user = userDetails.getUser();

        orderService.returnOrder(orderId,user);
        return ResponseEntity.ok("주문 반품 요청이 접수되었습니다. ");

    }
}
