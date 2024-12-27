package com.sparta.haengye_project.order.service;

import com.sparta.haengye_project.order.dto.OrderItemResponseDto;
import com.sparta.haengye_project.order.dto.OrderRequestDto;
import com.sparta.haengye_project.order.dto.OrderResponseDto;
import com.sparta.haengye_project.order.entity.Order;
import com.sparta.haengye_project.order.entity.OrderItem;
import com.sparta.haengye_project.order.entity.OrderItemStatus;
import com.sparta.haengye_project.order.entity.OrderStatus;
import com.sparta.haengye_project.order.exception.InvalidOrderStateException;
import com.sparta.haengye_project.order.exception.OrderNotFoundException;
import com.sparta.haengye_project.order.repository.OrderRepository;
import com.sparta.haengye_project.product.entitiy.Product;
import com.sparta.haengye_project.product.repository.ProductRepository;
import com.sparta.haengye_project.user.entity.User;
import com.sparta.haengye_project.wishlist.entity.WishListItem;
import com.sparta.haengye_project.wishlist.entity.Wishlist;
import com.sparta.haengye_project.wishlist.exception.WishlistNotFoundException;
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
        Wishlist wishlist = wishlistRepository.findById(user.getId())
                .orElseThrow(()-> new WishlistNotFoundException("위시리트가 존재하지 않습니다."));

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

    @Transactional
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

    @Transactional
    public void cancelOrder(Long orderId, User user) {
        Order order = orderRepository.findByIdAndUser(orderId, user)
                                     .orElseThrow(() -> new OrderNotFoundException("주문을 찾을 수 없습니다."));

        // 주문 상태 확인
        if (!order.getStatus().equals(OrderStatus.PENDING)) {
            throw new InvalidOrderStateException("배송 중 상태에서는 주문을 취소할 수 없습니다.");
        }

        // 주문 상태 변경
        order.setStatus(OrderStatus.CANCELLED);
        order.setCancelDate(LocalDateTime.now());

        // 주문 항목 상태 변경 및 재고 복구
        for (OrderItem item : order.getOrderItems()) {
            item.setStatus(OrderItemStatus.RETURNED);
            Product product = item.getProduct();
            product.setStock(product.getStock() + item.getQuantity()); // 재고 복구
        }

        orderRepository.save(order);
    }

    @Transactional
    public void returnOrder(Long orderId, User user) {
        Order order = orderRepository.findById(orderId)
                                     .orElseThrow(() -> new OrderNotFoundException("해당 주문을 찾을 수 없습니다."));

        // 사용자 비교: ID 기반으로 수정
        if (!order.getUser().getId().equals(user.getId())) {
            throw new IllegalArgumentException("본인의 주문만 반품할 수 있습니다.");
        }

        // 반품 가능 상태 확인
        if (!order.getStatus().equals(OrderStatus.COMPLETED)) {
            throw new IllegalStateException("배송 완료된 주문만 반품할 수 있습니다.");
        }

        // 반품 가능 시간 확인
        LocalDateTime now = LocalDateTime.now();
        if (now.isAfter(order.getDeliveryDate().plusMinutes(10))) { // D+1 기준
            throw new IllegalStateException("반품 가능 기간이 지났습니다.");
        }

        // 주문 상태 변경
        order.setStatus(OrderStatus.RETURN_REQUESTED);

        // 주문 항목 상태 변경
        for (OrderItem item : order.getOrderItems()) {
            item.setStatus(OrderItemStatus.RETURN_REQUESTED);
        }

        orderRepository.save(order);
    }
}
