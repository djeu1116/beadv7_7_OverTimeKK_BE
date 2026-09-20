package com.programmers.kdt.payment.client.order;

import com.programmers.kdt.payment.dto.PointEarnTarget;

import java.util.List;

public interface PointEarnTargetClient {

    List<PointEarnTarget> findEarnTargets(List<Long> ticketIds);
}
