package com.sparta.common.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class KafkaMessage<T> {

    private String type; // 이벤트 타입 (예: "ORDER_CREATED", "PAYMENT_PROCESSED")
    private T data; // 실제 데이터 (DTO 객체)

    public KafkaMessage(String type, T data) {
        this.type = type;
        this.data = data;
    }
}
