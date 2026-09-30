package com.programmers.kdt.order.domain.entity;

import com.programmers.kdt.common.exception.BusinessException;
import com.programmers.kdt.order.domain.exception.OrderErrorCode;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "order_item")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long orderItemId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Column(nullable = false)
    private Long ticketId;

    @Column(nullable = false)
    private Long ticketPrice;

    @Column(nullable = false)
    private String holdKey;

    private OrderItem(Long ticketId, Long ticketPrice, String holdKey){
        validateTicketPrice(ticketPrice);
        this.ticketId = ticketId;
        this.ticketPrice = ticketPrice;
        this.holdKey = holdKey;
    }

    public static OrderItem create(Long ticketId, Long ticketPrice, String holdKey){
        return new OrderItem(ticketId, ticketPrice, holdKey);

    }

    private void validateTicketPrice(Long ticketPrice){
        if(ticketPrice == null || ticketPrice <=0){
            throw new BusinessException(OrderErrorCode.INVALID_ORDER_AMOUNT);
        }
    }

    void assignOrder(Order order){
        this.order = order;
    }

}
