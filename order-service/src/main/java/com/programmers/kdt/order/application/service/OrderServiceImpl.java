package com.programmers.kdt.order.application.service;

import com.programmers.kdt.common.exception.BusinessException;
import com.programmers.kdt.order.infrastructure.client.TicketClient;
import com.programmers.kdt.order.infrastructure.client.TicketInfo;
import com.programmers.kdt.order.presentation.dto.CancelOrderRequest;
import com.programmers.kdt.order.presentation.dto.CancelOrderResponse;
import com.programmers.kdt.order.presentation.dto.CreateOrderRequest;
import com.programmers.kdt.order.presentation.dto.CreateOrderResponse;
import com.programmers.kdt.order.presentation.dto.GetOrderHistoryResponse;
import com.programmers.kdt.order.presentation.dto.OrderTicketRequest;
import com.programmers.kdt.order.presentation.dto.TicketReserveRequest;
import com.programmers.kdt.order.presentation.dto.ValidateTicketRequest;
import com.programmers.kdt.order.domain.entity.Order;
import com.programmers.kdt.order.domain.entity.OrderItem;
import com.programmers.kdt.order.domain.entity.OrderStatus;
import com.programmers.kdt.common.contract.OrderCancelRequestedEvent;
import com.programmers.kdt.order.domain.entity.TicketCancelJob;
import com.programmers.kdt.order.domain.entity.outbox.OrderOutboxEventType;
import com.programmers.kdt.order.domain.event.TicketCancelRequestEvent;
import com.programmers.kdt.order.domain.event.TicketReleaseRequestEvent;
import com.programmers.kdt.order.domain.exception.OrderErrorCode;
import com.programmers.kdt.order.infrastructure.repository.OrderRepository;
import com.programmers.kdt.order.infrastructure.repository.TicketCancelJobRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;


@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {
    private final OrderRepository orderRepository;
    private final TicketCancelJobRepository ticketCancelJobRepository;
    private final TicketClient ticketClient;
    private final ApplicationEventPublisher eventPublisher;
    private final OrderOutboxEventWriter orderOutboxEventWriter;

    // 주문 요청
    @Override
    @Transactional
    public CreateOrderResponse createOrder(CreateOrderRequest request, Long userId) {

        // 티켓 검증
        ValidateTicketRequest ticketRequest = ValidateTicketRequest.from(request, userId);
        ticketClient.validateTicket(ticketRequest);

        // 주문 항목 생성 - 현재는 티켓 1매만 가능
        OrderItem item = OrderItem.create(request.ticketId(), request.price(), request.holdKey());

        // 주문 생성 및 저장 -- 티켓 만료 시각을 주문 만료 시각으로 설정
        Order order = Order.create(userId, List.of(item), request.expiredAt());
        Order savedOrder = orderRepository.save(order);

        return CreateOrderResponse.from(savedOrder);
    }

    @Override
    @Transactional
    // 결제 전 주문 취소
    public CancelOrderResponse cancelPendingOrder(Long orderId, Long userId){
        Order order = findOrder(orderId);
        if(!userId.equals(order.getUserId())) throw new BusinessException(OrderErrorCode.ORDER_ACCESS_DENIED);
        order.cancelPending();

        publishTicketReleaseEvent(order);

        return CancelOrderResponse.from(order);
    }

    // 주문 완료
    @Override
    @Transactional
    public void completeOrder(Long orderId){
        Order order = findOrder(orderId);

        // 이미 완료된 주문이면 중복 처리 X
        if(order.isCompleted()){
            return;
        }

        ticketClient.reserveTicket(new TicketReserveRequest(
                order.getTicketId(),
                order.getItems().getFirst().getHoldKey(),
                order.getUserId()
        ));

        order.complete();
    }

    // 주문 만료
    @Override
    @Transactional
    public void expireOrder(Long orderId, LocalDateTime now){
        int updatedRow = orderRepository.tryExpire(
                orderId,
                OrderStatus.PENDING,
                OrderStatus.EXPIRED,
                now
        );
        if(updatedRow == 0){
            return;
        }

        Order order = findOrder(orderId);
        publishTicketReleaseEvent(order);
    }

    // 결제 후 주문 취소
    @Override
    @Transactional
    public CancelOrderResponse cancelCompletedOrder(Long orderId, Long userId, CancelOrderRequest request) {
        Order order = findOrderForUpdate(orderId);

        if(!userId.equals(order.getUserId())) throw new BusinessException(OrderErrorCode.ORDER_ACCESS_DENIED);
      
        // 이미 취소 접수된 주문의 재요청은 동일 응답을 반환
        if (order.isCancelRequested()) {
            return CancelOrderResponse.from(order);
        }

        // 취소 가능한 주문인지 검증
        order.validateCancel();

        // 환불 결과가 나오기 전까지는 취소 접수 상태와 티켓 예약을 유지
        order.requestCancel();

        // 결제 취소는 동기 호출이 아니라 outbox로 넘긴다 - 접수 커밋과 같은 트랜잭션이라 유실되지 않음
        orderOutboxEventWriter.enqueue(OrderOutboxEventType.ORDER_CANCEL_REQUESTED, order.getOrderId(),
                new OrderCancelRequestedEvent(order.getOrderId(), request.reason()));

        return CancelOrderResponse.from(order);
    }

    @Override
    @Transactional
    public void confirmCancellation(Long orderId) {
        Order order = findOrderForUpdate(orderId);
        if (order.confirmCancel()) {
            ticketCancelJobRepository.save(
                    TicketCancelJob.create(order.getOrderId(), order.getTicketId(), order.getUserId())
            );
            publishTicketCancelEvent(order);
        }
    }

    @Override
    @Transactional
    public void revertCancellation(Long orderId) {
        Order order = findOrderForUpdate(orderId);
        order.revertCancel();
    }

    @Override
    @Transactional(readOnly = true)
    public List<GetOrderHistoryResponse> getOrderHistory(Long userId) {
        List<Order> orders =
                orderRepository.findByUserIdAndOrderStatusInOrderByCreatedAtDesc(
                        userId,
                        List.of(OrderStatus.CANCELLED, OrderStatus.COMPLETED, OrderStatus.EXPIRED)
                );

        if (orders.isEmpty()) {
            return List.of();
        }

        List<Long> ticketIds = orders.stream()
                .map(Order::getTicketId)
                .toList();
        List<TicketInfo> ticketInfos = ticketClient.getTickets(new OrderTicketRequest(ticketIds));

        Map<Long, TicketInfo> ticketInfoMap = ticketInfos.stream()
                .collect(Collectors.toMap(
                        TicketInfo::ticketId,
                        Function.identity()
                ));

        return orders.stream()
                .map(order -> {
                    TicketInfo ticketInfo =
                            ticketInfoMap.get(order.getTicketId());

                    if (ticketInfo == null) {
                        throw new BusinessException(
                                OrderErrorCode.TICKET_INFO_NOT_FOUND
                        );
                    }

                    return GetOrderHistoryResponse.from(order, ticketInfo);
                })
                .toList();
    }

    private Order findOrder(Long orderId){
        return orderRepository.findById(orderId)
                .orElseThrow(()->
                        new BusinessException(OrderErrorCode.ORDER_NOT_FOUND));
    }

    private Order findOrderForUpdate(Long orderId) {
        return orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new BusinessException(OrderErrorCode.ORDER_NOT_FOUND));
    }

    private void publishTicketReleaseEvent(Order order) {
        OrderItem orderItem = order.getItems().getFirst();

        eventPublisher.publishEvent(
                new TicketReleaseRequestEvent(
                        order.getOrderId(),
                        orderItem.getTicketId(),
                        orderItem.getHoldKey()
                )
        );
    }

    private void publishTicketCancelEvent(Order order){
        eventPublisher.publishEvent(
                new TicketCancelRequestEvent(order.getTicketId(), order.getUserId(), order.getOrderId())
        );
    }

    @Override
    @Transactional
    public void failOrderAfterCompensation(Long orderId) {
        Order order = findOrderForUpdate(orderId);
        if (order.failAfterPayment()) {
            publishTicketReleaseEvent(order);
        }
    }

    @Override
    @Transactional
    public void handlePaymentFailed(Long orderId){
        Order order = findOrder(orderId);
        order.failPayment();
    }

}