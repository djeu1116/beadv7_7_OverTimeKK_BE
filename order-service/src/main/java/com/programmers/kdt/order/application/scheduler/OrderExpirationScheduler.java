package com.programmers.kdt.order.application.scheduler;

import com.programmers.kdt.order.domain.entity.OrderStatus;
import com.programmers.kdt.order.infrastructure.repository.OrderRepository;
import com.programmers.kdt.order.application.service.OrderServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderExpirationScheduler {

    private final OrderServiceImpl orderServiceImpl;
    private final OrderRepository orderRepository;

    @Scheduled(fixedDelay = 10, timeUnit = TimeUnit.SECONDS)
    public void expireOrder(){
        LocalDateTime now = LocalDateTime.now();
        List<Long> orderIds = orderRepository.findExpirationCandidateIds(OrderStatus.PENDING, now);

        for(Long orderId : orderIds){
            try{
                orderServiceImpl.expireOrder(orderId, now);
            }catch (RuntimeException e){
                log.error("주문 만료 실패 orderId={}", orderId, e);
            }
        }
    }
}
