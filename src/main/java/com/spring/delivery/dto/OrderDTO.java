package com.spring.delivery.dto;

import lombok.Data;

import java.util.List;

@Data
public class OrderDTO {
    private Long orderId;
    private Long userId;
    private List<Long> menus;
    private int totalPrice;
    private Long storeId;
}