package com.sparta.haengye_project.order.controller;

import com.sparta.haengye_project.order.dto.OrderRequestDto;
import com.sparta.haengye_project.order.dto.OrderResponseDto;
import com.sparta.haengye_project.order.service.OrderService;
import com.sparta.haengye_project.security.UserDetailsImpl;
import com.sparta.haengye_project.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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

}
