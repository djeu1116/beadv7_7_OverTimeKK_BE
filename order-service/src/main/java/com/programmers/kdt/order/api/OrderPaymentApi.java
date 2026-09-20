package com.programmers.kdt.order.api;

import java.util.List;
import java.util.Optional;

// 주문이 결제 컨텍스트에 제공하는 API. 주문/결제를 서비스로 분리하면 이 인터페이스가 내부 엔드포인트 명세가 된다.
// 실패를 예외가 아닌 결과값으로 돌려주고, 호출 측이 자기 에러 체계로 해석한다.
public interface OrderPaymentApi {

    Optional<OrderSnapshot> findOrder(Long orderId);

    // 결제 시작을 위한 주문 상태 전이(PENDING -> PAYMENT_STARTED). 만료 주문은 전이하지 않는다.
    StartPaymentResult startPayment(Long orderId);

    Long findTicketId(Long orderId);

    List<PointEarnTargetView> findPointEarnTargets(List<Long> ticketIds);
}
