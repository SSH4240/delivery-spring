package com.spring.delivery.service;

import com.spring.delivery.domain.*;
import com.spring.delivery.dto.OrderDTO;
import com.spring.delivery.dto.OrderItemDTO;
import com.spring.delivery.dto.SocketMessageForm;
import com.spring.delivery.exception.*;
import com.spring.delivery.repository.*;
import jakarta.annotation.PostConstruct;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

@Service
@Transactional
@RequiredArgsConstructor
public class OrderService {
    private final OrderRepository orderRepository;
    private final UserRepository userRepository;
    private final MenuRepository menuRepository;
    private final StoreRepository storeRepository;
    private final ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();

    @PostConstruct
    public void init() {
        executorService.scheduleAtFixedRate(this::checkUnacceptedOrders, 1, 1, TimeUnit.MINUTES);
    }

    private void checkUnacceptedOrders(){
        LocalDateTime currentTime = LocalDateTime.now();
        List<Order> orders = new ArrayList<>();
        if (orderRepository.findAllByStatus(OrderStatus.ORDER).isPresent())
            orders = orderRepository.findAllByStatus(OrderStatus.ORDER).get();

        for (Order order : orders){
            if (order.getOrderTime().plusMinutes(1).isBefore(currentTime))
                cancel(order.getId());
        }
    }

    public SocketMessageForm create(OrderDTO orderDTO){
        SocketMessageForm messageForm = new SocketMessageForm(true);

        if (orderDTO.getTotalPrice() < 6000){
            throw new InvalidOrderException("최소 주문 금액 6000원을 넘어야 주문이 가능합니다.");
        }

        Store store = storeRepository.findById(orderDTO.getStoreId()).get();
        int storeOpen = store.getOpenTime();
        int storeClosed = store.getClosedTime();
        if (orderDTO.getOrderTime().getHour() < storeOpen || orderDTO.getOrderTime().getHour() > storeClosed) {
            throw new InvalidOrderException("가게 운영 시간이 아닙니다.");
        }

        boolean haveMainMenu = false;
        for (OrderItemDTO orderItemDTO : orderDTO.getOrderItem()){
            if (menuRepository.findById(orderItemDTO.getMenuId()).get().getMenuType().equals(MenuType.MAIN))
                haveMainMenu = true;
        }
        if (!haveMainMenu) {
            throw new InvalidOrderException("사이드 메뉴 만으로는 주문이 불가능합니다.");
        }


        Order order = new Order();

        order.setUser(userRepository.findById(orderDTO.getUserId()).get());
        order.setStatus(OrderStatus.ORDER);
        order.setOrderTime(LocalDateTime.now());

        for (OrderItemDTO orderItemDTO : orderDTO.getOrderItem()){
            OrderItem item = new OrderItem();

            item.setMenu(menuRepository.findById(orderItemDTO.getMenuId()).get());
            item.setQuantity(orderItemDTO.getQuantity());
            order.addOrderItem(item);
        }

        orderRepository.save(order);

        messageForm.setMessage("주문 접수가 완료되었습니다.");
        return messageForm;
    }

    public SocketMessageForm cancel(Long orderId){
        SocketMessageForm messageForm = new SocketMessageForm(true);

        Order order = orderRepository.findById(orderId).get();
        if (order.getStatus().equals(OrderStatus.DELIVERY)) {
            throw new InvalidOrderException("배달중인 주문은 취소가 불가능합니다.");
        }
        else if (order.getStatus().equals(OrderStatus.COMPLETED)) {
            throw new InvalidOrderException("이미 배달이 완료된 주문은 취소가 불가능합니다.");
        }
        else if (order.getStatus().equals(OrderStatus.CANCELLED)) {
            throw new InvalidOrderException("이미 취소된 주문은 취소가 불가능합니다.");
        }

        order.setStatus(OrderStatus.CANCELLED);
        orderRepository.save(order);

        messageForm.setMessage("주문 취소가 완료되었습니다.");
        return messageForm;
    }

    public List<Order> findAllOrdersByUserId(Long userId){
        if (orderRepository.findAllByUserId(userId).isPresent())
            return orderRepository.findAllByUserId(userId).get();
        return null;
    }
    public List<Order> findAllOrders(){
//        if (orderRepository.findAllByStatus(OrderStatus.ORDER).isPresent())
//            return orderRepository.findAllByStatus(OrderStatus.ORDER).get();
        return orderRepository.findAll();
    }

    public SocketMessageForm acceptOrder(Long orderId){
        SocketMessageForm messageForm = new SocketMessageForm(true);

        Order order = orderRepository.findById(orderId).get();
        if (!order.getStatus().equals(OrderStatus.ORDER)) {
            throw new InvalidOrderException("주문 상태의 주문만 수락할 수 있습니다.");
        }
        order.setStatus(OrderStatus.DELIVERY);
        orderRepository.save(order);

        messageForm.setMessage("주문을 수락하였습니다.");
        return messageForm;
    }

    public SocketMessageForm denyOrder(Long orderId){
        SocketMessageForm messageForm = new SocketMessageForm(true);

        Order order = orderRepository.findById(orderId).get();
        if (!order.getStatus().equals(OrderStatus.ORDER)) {
            throw new InvalidOrderException("접수된 주문만 수락할 수 있습니다.");
        }
        order.setStatus(OrderStatus.CANCELLED);
        orderRepository.save(order);

        messageForm.setMessage("주문을 거절하였습니다.");
        return messageForm;
    }

    public SocketMessageForm setOrderDelivered(Long orderId){
        SocketMessageForm messageForm = new SocketMessageForm(true);


        Order order = orderRepository.findById(orderId).get();

        if (!order.getStatus().equals(OrderStatus.DELIVERY)) {
            throw new InvalidOrderException("배달 상태의 주문만 완료 처리할 수 있습니다.");
        }
        order.setStatus(OrderStatus.COMPLETED);
        orderRepository.save(order);
        messageForm.setMessage("배달이 완료되었습니다.");
        return messageForm;
    }
}
