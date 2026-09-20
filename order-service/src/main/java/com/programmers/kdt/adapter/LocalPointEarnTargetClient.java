package com.programmers.kdt.adapter;

import com.programmers.kdt.order.api.OrderPaymentApi;
import com.programmers.kdt.payment.client.order.PointEarnTargetClient;
import com.programmers.kdt.payment.dto.PointEarnTarget;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.util.List;

@Profile("!order-api")
@Component
@RequiredArgsConstructor
public class LocalPointEarnTargetClient implements PointEarnTargetClient {

    private final OrderPaymentApi orderPaymentApi;

    @Override
    public List<PointEarnTarget> findEarnTargets(List<Long> ticketIds) {
        return orderPaymentApi.findPointEarnTargets(ticketIds).stream()
                .map(view -> new PointEarnTarget(view.userId(), view.ticketId(), view.ticketPrice()))
                .toList();
    }
}
