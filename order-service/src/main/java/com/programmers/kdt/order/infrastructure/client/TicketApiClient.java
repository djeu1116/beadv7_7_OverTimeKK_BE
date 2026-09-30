package com.programmers.kdt.order.infrastructure.client;

import com.programmers.kdt.common.exception.BusinessException;
import com.programmers.kdt.common.response.ApiResponse;
import com.programmers.kdt.order.presentation.dto.OrderTicketRequest;
import com.programmers.kdt.order.presentation.dto.TicketCancelRequest;
import com.programmers.kdt.order.presentation.dto.TicketReleaseRequest;
import com.programmers.kdt.order.presentation.dto.TicketReserveRequest;
import com.programmers.kdt.order.presentation.dto.ValidateTicketRequest;
import com.programmers.kdt.order.domain.exception.OrderErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class TicketApiClient implements TicketClient {

    private final RestClient performanceRestClient;

    @Override
    public void validateTicket(ValidateTicketRequest request){
        try{
            performanceRestClient.post()
                    .uri("/api/tickets/hold/validation")
                    .body(request)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientException e){
            log.error("티켓 검증 실패 ticketId={}", request.ticketId(), e);
            if (e instanceof RestClientResponseException rcre
                    && rcre.getStatusCode().value() == HttpStatus.NOT_FOUND.value()) {
                throw new BusinessException(OrderErrorCode.TICKET_NOT_FOUND);
            }
            throw new BusinessException(OrderErrorCode.TICKET_VALIDATION_FAILED);
        }
    }

    @Override
    public void reserveTicket(TicketReserveRequest request){
        try {
            performanceRestClient.put()
                    .uri("/api/tickets/status/reserved")
                    .body(request)
                    .retrieve()
                    .toBodilessEntity();

        } catch (RestClientException e) {
            log.error("티켓 점유 실패 ticketId={}", request.ticketId(), e);
            if (e instanceof RestClientResponseException rcre
                    && rcre.getStatusCode().value() == HttpStatus.NOT_FOUND.value()) {
                throw new BusinessException(OrderErrorCode.TICKET_NOT_FOUND);
            }
            throw new BusinessException(OrderErrorCode.TICKET_RESERVE_FAILED);
        }
    }
    @Override
    public void releaseSeat(TicketReleaseRequest request) {
        try {
            performanceRestClient.put()
                    .uri("/api/tickets/status/release")
                    .body(request)
                    .retrieve()
                    .toBodilessEntity();

        } catch (RestClientException e) {
            log.error("티켓 점유 해제 실패 ticketId={}", request.ticketId(), e);
            if(e instanceof RestClientResponseException rcre
                    && rcre.getStatusCode().value() == HttpStatus.NOT_FOUND.value()){
                throw new BusinessException(OrderErrorCode.TICKET_NOT_FOUND);
            }
            throw new BusinessException(OrderErrorCode.TICKET_RELEASE_FAILED);
        }
    }

    @Override
    public void cancelTicket(TicketCancelRequest request) {
        try {
            performanceRestClient.put()
                    .uri("/api/tickets/status/canceled/release")
                    .body(request)
                    .retrieve()
                    .toBodilessEntity();

        } catch (RestClientException e) {
            log.error("티켓 취소에 따른 좌석 점유 해제 실패 ticketId={}", request.ticketId(), e);
            if(e instanceof RestClientResponseException rcre
                    && rcre.getStatusCode().value() == HttpStatus.NOT_FOUND.value()) {
                throw new BusinessException(OrderErrorCode.TICKET_NOT_FOUND);
            }
            throw new BusinessException(OrderErrorCode.TICKET_RELEASE_FAILED);
        }
    }

    @Override
    public List<TicketInfo> getTickets(OrderTicketRequest request){
        try {
            ApiResponse<List<TicketInfo>> response = performanceRestClient.post()
                    .uri("/api/tickets/orders")
                    .body(request)
                    .retrieve()
                    .body(new ParameterizedTypeReference<
                            ApiResponse<List<TicketInfo>>
                            >() {});

            if (response == null || response.data() == null) {
                throw new BusinessException(OrderErrorCode.TICKET_INFO_RESPONSE_EMPTY);
            }

            return response.data();

        } catch (RestClientException e) {
            log.error("티켓 정보 조회 실패 ticketIds={}", request.ticketIds(), e);
            throw new BusinessException(OrderErrorCode.TICKET_INFO_GET_FAILED);
        }
    }
}
