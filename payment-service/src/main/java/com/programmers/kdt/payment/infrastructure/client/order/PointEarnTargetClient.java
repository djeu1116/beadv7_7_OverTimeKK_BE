package com.programmers.kdt.payment.infrastructure.client.order;

import com.programmers.kdt.payment.presentation.dto.PointEarnTarget;

import java.util.List;

public interface PointEarnTargetClient {

    List<PointEarnTarget> findEarnTargets(List<Long> ticketIds);
}
