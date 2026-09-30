package com.programmers.kdt.order.domain.entity;

import com.programmers.kdt.common.entity.BaseTimeEntity;
import com.programmers.kdt.common.exception.BusinessException;
import com.programmers.kdt.order.domain.exception.OrderErrorCode;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Entity
@Table(
        name = "orders",
        indexes = {
                @Index(
                        name = "idx_order_status_expires_at",
                        columnList = "order_status, expires_at"
                )
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Order extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long orderId;

    @Column(nullable = false)
    private Long userId;

    @OneToMany(mappedBy = "order", cascade = CascadeType.PERSIST)
    private List<OrderItem> items = new ArrayList<>();

    @Column(nullable = false)
    private Long totalAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus orderStatus;

    @Column(nullable = false)
    private LocalDate orderDate;

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    private LocalDateTime cancelledAt;


    private Order(Long userId, List<OrderItem> items, LocalDateTime expiresAt){
        validateItems(items);
        this.userId = userId;
        this.orderStatus = OrderStatus.PENDING;
        this.orderDate = LocalDate.now();
        this.expiresAt = expiresAt;

        for(OrderItem item : items){
            assignOrderItem(item);
        }
        this.totalAmount = calculateAmount();
    }

    // 주문 생성
    public static Order create(Long userId, List<OrderItem> items, LocalDateTime expiresAt){
        return new Order(userId, items, expiresAt);
    }

    // 주문 검증
    private void validateItems(List<OrderItem> items){
        if(items == null || items.isEmpty() || items.stream().anyMatch(Objects::isNull)){
            throw new BusinessException(OrderErrorCode.ORDER_ITEMS_REQUIRED);
        }
    }

    // 주문항목 추가
    private void assignOrderItem(OrderItem item){
        this.items.add(item);
        item.assignOrder(this);
    }

    // 총 주문 금액 계산
    private Long calculateAmount(){
        return items.stream()
                .mapToLong(OrderItem::getTicketPrice)
                .sum();
    }

    // 결제 전 주문 취소 PENDING -> CANCELED
    public void cancelPending(){
        if(orderStatus != OrderStatus.PENDING){
            throw new BusinessException(OrderErrorCode.ORDER_NOT_PENDING);
        }
        this.orderStatus = OrderStatus.CANCELLED;
        this.cancelledAt = LocalDateTime.now();

    }

    public boolean isCompleted(){
        return orderStatus==OrderStatus.COMPLETED;
    }

    public boolean isCancelRequested(){
        return orderStatus == OrderStatus.CANCEL_REQUESTED;
    }

    // 주문 완료 PAYMENT_STARTED -> COMPLETED
    public void complete(){
        if(orderStatus == OrderStatus.COMPLETED){
            return;
        }
        if(orderStatus != OrderStatus.PAYMENT_STARTED){
            throw new BusinessException(OrderErrorCode.ORDER_NOT_PAYMENT_STARTED);
        }
        this.orderStatus = OrderStatus.COMPLETED;
    }

    // 주문 만료 PENDING -> EXPIRED
    public void expire(){
        if(orderStatus == OrderStatus.EXPIRED){
            return; // 이미 주문 만료된 상태, 중복 무시
        }
        if(orderStatus != OrderStatus.PENDING){
            throw new BusinessException(OrderErrorCode.ORDER_NOT_PENDING);
        }
        this.orderStatus = OrderStatus.EXPIRED;
    }

    // 결제 시작 PENDING -> PAYMENT_STARTED
    public void startPayment(LocalDateTime now) {
        if (orderStatus != OrderStatus.PENDING) {
            throw new BusinessException(
                    OrderErrorCode.ORDER_NOT_PENDING
            );
        }

        if (!expiresAt.isAfter(now)) {
            throw new BusinessException(
                    OrderErrorCode.ORDER_ALREADY_EXPIRED
            );
        }

        orderStatus = OrderStatus.PAYMENT_STARTED;
    }

    // 결제 실패 후 재결제
    public void failPayment(){
        if(orderStatus == OrderStatus.PENDING){
            return;
        }
        if(orderStatus != OrderStatus.PAYMENT_STARTED){
            throw new BusinessException(OrderErrorCode.ORDER_NOT_PAYMENT_STARTED);
        }
        orderStatus = OrderStatus.PENDING;
    }

    // 취소 가능한 주문인지 검증
    public void validateCancel(){
        if(orderStatus == OrderStatus.CANCELLED){
            throw new BusinessException(OrderErrorCode.ORDER_ALREADY_CANCELLED);
        }
        if(orderStatus != OrderStatus.COMPLETED){
            throw new BusinessException(OrderErrorCode.ORDER_NOT_COMPLETED);
        }
    }

    // 결제 완료 후 주문 취소 접수 COMPLETED -> CANCEL_REQUESTED
    public void requestCancel() {
        validateCancel();
        this.orderStatus = OrderStatus.CANCEL_REQUESTED;
    }

    // 환불 성공 후 주문 취소 확정 CANCEL_REQUESTED -> CANCELLED
    public boolean confirmCancel() {
        if (orderStatus == OrderStatus.CANCELLED) {
            return false; // 티켓 취소 이벤트의 중복 발행 방지
        }
        validateCancelRequested();
        this.orderStatus = OrderStatus.CANCELLED;
        this.cancelledAt = LocalDateTime.now();
        return true;
    }

    // 환불 실패 후 주문 상태 복구 CANCEL_REQUESTED -> COMPLETED
    public void revertCancel() {
        if (orderStatus == OrderStatus.COMPLETED) {
            return;
        }
        validateCancelRequested();
        this.orderStatus = OrderStatus.COMPLETED;
    }

    // 결제는 확정됐지만 후속 단계(티켓 예약)가 영구 실패해 시스템이 환불(보상)까지 끝낸 뒤 주문 종료 PAYMENT_STARTED -> CANCELLED
    // 고객 환불(COMPLETED -> CANCEL_REQUESTED -> CANCELLED)과 달리 주문이 완료된 적이 없어서 별도 전이로 둠
    public boolean failAfterPayment() {
        if (orderStatus == OrderStatus.CANCELLED) {
            return false;
        }
        if (orderStatus != OrderStatus.PAYMENT_STARTED) {
            throw new BusinessException(OrderErrorCode.ORDER_NOT_PAYMENT_STARTED);
        }
        this.orderStatus = OrderStatus.CANCELLED;
        this.cancelledAt = LocalDateTime.now();
        return true;
    }

    private void validateCancelRequested() {
        if (orderStatus != OrderStatus.CANCEL_REQUESTED) {
            throw new BusinessException(OrderErrorCode.ORDER_CANCEL_NOT_REQUESTED);
        }
    }

    // 티켓 ID
    public Long getTicketId(){
        return items.getFirst().getTicketId();
    }

}
