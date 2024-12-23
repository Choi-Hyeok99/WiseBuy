package com.sparta.haengye_project.order.service;

import com.sparta.haengye_project.order.dto.OrderItemResponseDto;
import com.sparta.haengye_project.order.dto.OrderRequestDto;
import com.sparta.haengye_project.order.dto.OrderResponseDto;
import com.sparta.haengye_project.order.entity.Order;
import com.sparta.haengye_project.order.entity.OrderItem;
import com.sparta.haengye_project.order.entity.OrderItemStatus;
import com.sparta.haengye_project.order.entity.OrderStatus;
import com.sparta.haengye_project.order.repository.OrderRepository;
import com.sparta.haengye_project.product.entitiy.Product;
import com.sparta.haengye_project.product.repository.ProductRepository;
import com.sparta.haengye_project.user.entity.User;
import com.sparta.haengye_project.wishlist.entity.WishListItem;
import com.sparta.haengye_project.wishlist.entity.Wishlist;
import com.sparta.haengye_project.wishlist.repository.WishlistRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class OrderService {

    private final ProductRepository productRepository;
    private final WishlistRepository wishlistRepository;
    private final OrderRepository orderRepository;


    @Transactional
    public OrderResponseDto createOrder(OrderRequestDto requestDto, User user, String address) {

        // 1. 위시리스트 확인
        Wishlist wishlist = wishlistRepository.findByUser(user)
                .orElseThrow(()-> new IllegalArgumentException("위시리트가 존재하지 않습니다."));

        List<WishListItem> wishListItems = wishlist.getItems();

        if (wishListItems.isEmpty()){
            throw new IllegalArgumentException("위시 리스트에 상품이 없습니다.");
        }

        // 주문 생성
        Order order = new Order();
        order.setUser(user);
        order.setOrderDate(LocalDateTime.now());
        order.setStatus(OrderStatus.PENDING); // 기본 상태
        order.setShippingAddress(user.getAddress());
        order.setDeliveryDate(LocalDateTime.now().plusDays(2)); // 배송 완료 예상일 D+2
        order.setCancelDate(null); // 생성 시 초기화
        orderRepository.save(order);

        // 총 금액
        int totalAmount = 0;


        // 위시리스트 기반 주문 항목 생성
       for (WishListItem item : wishListItems){
           Product product = item.getProduct();
           int quantity = item.getQuantity();

           // 주문 항목 생성
           OrderItem orderItem = new OrderItem();
           orderItem.setOrder(order);
           orderItem.setProduct(product);
           orderItem.setQuantity(quantity);
           orderItem.setPrice(product.getProductInfo().getPrice() * quantity);
           orderItem.setStatus(OrderItemStatus.ORDERED); // OrderItemStatus로 변경
           // 재고 차감
           product.setStock(product.getStock() - quantity);

           // 주문에 추가
           order.getOrderItems().add(orderItem);

           // 총 금액 계산
           totalAmount += orderItem.getPrice();
       }
       // 5. 주문 총 금액 설정
        order.setTotalAmount(totalAmount);

       // 6. 주문 저장
        orderRepository.save(order);
        return new OrderResponseDto(order);
    }

    @Transactional(readOnly = true)
    public List<OrderResponseDto> getOrders(User user) {
        // 사용자에 대한 모든 주문 조회
        List<Order> orders = orderRepository.findAllByUser(user);
        List<OrderResponseDto> responseDtos = new ArrayList<>();

        // 주문 목록 처리
        for (int i = 0; i < orders.size(); i++) {
            Order order = orders.get(i);
            List<OrderItemResponseDto> items = new ArrayList<>();

            // 각 주문 항목의 상태 계산
            List<OrderItem> orderItems = order.getOrderItems();
            for (int j = 0; j < orderItems.size(); j++) {
                OrderItem item = orderItems.get(j);
                OrderItemStatus itemStatus = calculateOrderItemStatus(order.getOrderDate());
                item.setStatus(itemStatus);

                items.add(new OrderItemResponseDto(
                        item.getProduct().getId(),
                        item.getProduct().getProductName(),
                        item.getQuantity(),
                        item.getPrice(),
                        item.getStatus().name()
                ));
            }
            // OrderResponseDto로 변환하여 추가
            responseDtos.add(new OrderResponseDto(
                    order.getId(),
                    order.getOrderDate(),
                    order.getShippingAddress(),
                    order.getStatus(),
                    order.getTotalAmount(),
                    order.getDeliveryDate(),
                    order.getCancelDate(),
                    items
            ));
        }

        return responseDtos;
    }
    private OrderItemStatus calculateOrderItemStatus(LocalDateTime orderDate) {
        LocalDateTime now = LocalDateTime.now();
        if (now.isAfter(orderDate.plusDays(2))) {
            return OrderItemStatus.DELIVERED;
        } else if (now.isAfter(orderDate.plusDays(1))) {
            return OrderItemStatus.SHIPPED;
        }
        return OrderItemStatus.ORDERED;
    }
}
