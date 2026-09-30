package com.programmers.kdt.order.application.api;

import com.programmers.kdt.common.exception.BusinessException;
import com.programmers.kdt.common.exception.CommonErrorCode;
import com.programmers.kdt.order.domain.entity.Order;
import com.programmers.kdt.order.domain.entity.OrderItem;
import com.programmers.kdt.order.domain.entity.OrderStatus;
import com.programmers.kdt.order.infrastructure.repository.OrderItemRepository;
import com.programmers.kdt.order.infrastructure.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class OrderPaymentApiImpl implements OrderPaymentApi {

    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;

    @Override
    @Transactional(readOnly = true)
    public Optional<OrderSnapshot> findOrder(Long orderId) {
        return orderRepository.findById(orderId)
                .map(order -> new OrderSnapshot(order.getOrderId(), order.getUserId(), order.getTotalAmount()));
    }

    // 같은 프로세스에 있는 동안은 호출자(결제)의 트랜잭션에 함께 참여해서 기존 원자성을 유지한다.
    // 서비스를 분리하면 이 원자성이 깨지므로 그 시점에 실패 보상이 필요해진다.
    @Override
    @Transactional
    public StartPaymentResult startPayment(Long orderId) {
        Optional<Order> found = orderRepository.findById(orderId);
        if (found.isEmpty()) {
            return StartPaymentResult.NOT_FOUND;
        }

        LocalDateTime now = LocalDateTime.now();
        int updatedRow = orderRepository.tryStartPayment(
                orderId,
                OrderStatus.PENDING,
                OrderStatus.PAYMENT_STARTED,
                now
        );

        if (updatedRow > 0) {
            return StartPaymentResult.STARTED;
        }
        return found.get().getExpiresAt().isAfter(now)
                ? StartPaymentResult.NOT_PENDING
                : StartPaymentResult.EXPIRED;
    }

    @Override
    @Transactional
    public void cancelPaymentStart(Long orderId) {
        orderRepository.findById(orderId).ifPresent(Order::failPayment);
    }

    @Override
    @Transactional(readOnly = true)
    public Long findTicketId(Long orderId) {
        return orderItemRepository.findFirstByOrderId(orderId)
                .map(OrderItem::getTicketId)
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
    }

    @Override
    @Transactional(readOnly = true)
    public List<PointEarnTargetView> findPointEarnTargets(List<Long> ticketIds) {
        return orderItemRepository.findPointEarnTargets(ticketIds);
    }
}
