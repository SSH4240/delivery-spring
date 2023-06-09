package com.spring.delivery.domain;

import jakarta.persistence.*;
import lombok.Data;

@Data
@Entity
public class Menu {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String name;
    private MenuType menuType;
    private DiscountPolicy discountPolicy;
    private int price;

    private String imageName;   //  이미지 저장 타입

    @ManyToOne
    @JoinColumn(name="STORE_ID")
    private Store store;

    @OneToOne
    private Statistics statistics;

    @OneToOne(mappedBy = "menu")
    private OrderItem orderItem;

}