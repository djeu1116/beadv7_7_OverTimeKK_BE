package com.programmers.kdt.ticket.presentation.controller;

import com.programmers.kdt.common.response.ApiResponse;
import com.programmers.kdt.ticket.presentation.dto.CancelTicketStatusRequest;
import com.programmers.kdt.ticket.presentation.dto.CheckTicketHoldAvailableRequest;
import com.programmers.kdt.ticket.presentation.dto.CheckTicketHoldAvailableResponse;
import com.programmers.kdt.ticket.presentation.dto.CreateStandbyResponse;
import com.programmers.kdt.ticket.presentation.dto.OrderTicketRequest;
import com.programmers.kdt.ticket.presentation.dto.OrderTicketResponse;
import com.programmers.kdt.ticket.presentation.dto.ReleaseTicketHoldRequest;
import com.programmers.kdt.ticket.presentation.dto.ReservedTicketRequest;
import com.programmers.kdt.ticket.presentation.dto.SessionStartDateResponse;
import com.programmers.kdt.ticket.presentation.dto.TicketZoneRequest;
import com.programmers.kdt.ticket.presentation.dto.TicketZonesResponse;
import com.programmers.kdt.ticket.presentation.dto.ValidateTicketHoldRequest;
import com.programmers.kdt.ticket.application.service.TicketHoldService;
import com.programmers.kdt.ticket.application.service.TicketReleaseService;
import com.programmers.kdt.ticket.application.service.TicketReserveService;
import com.programmers.kdt.ticket.application.service.TicketService;
import com.programmers.kdt.ticket.application.service.ValidateTicketHoldService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/tickets")
@RequiredArgsConstructor
public class TicketController {

    private final TicketService ticketService;
    private final TicketHoldService ticketHoldService;
    private final TicketReleaseService ticketReleaseService;
    private final TicketReserveService ticketReserveService;
    private final ValidateTicketHoldService validateTicketHoldService;

    @PostMapping("/standby")
    public CreateStandbyResponse issueStandby(
            @RequestParam Long userId, @RequestParam Long sessionNum, @RequestParam String zone) {
        return ticketService.issueStandby(userId, sessionNum, zone);
    }

    @GetMapping("/{ticketId}/refund-date")
    public ApiResponse<SessionStartDateResponse> getSessionStartDateResponse(@PathVariable Long ticketId) {
        SessionStartDateResponse response = ticketService.getSessionStartDate(ticketId);
        return ApiResponse.success(response);
    }

    @PutMapping("/status/hold")
    public ApiResponse<CheckTicketHoldAvailableResponse> checkTicketHoldAvailable(
            @Valid @RequestBody CheckTicketHoldAvailableRequest request,
            @RequestHeader("X-User-Id") Long userId) {
        CheckTicketHoldAvailableResponse response = ticketHoldService.checkTicketHoldStatus(request, userId);
        return ApiResponse.success(response);
    }

    @PutMapping("/status/release")
    public ApiResponse<?> releaseHoldTicket(@Valid @RequestBody ReleaseTicketHoldRequest request) {
        ticketReleaseService.releaseHoldTicket(request);
        return ApiResponse.success(null);
    }

    @PutMapping("/status/canceled/release")
    public ApiResponse<?> releaseCanceledTicket(@Valid @RequestBody CancelTicketStatusRequest request) {
        ticketReleaseService.cancelReservedTicket(request);
        return ApiResponse.success(null);
    }

    @PutMapping("/status/reserved")
    public ApiResponse<?> reservedTicket(@Valid @RequestBody ReservedTicketRequest request) {
        ticketReserveService.reservedTicket(request);
        return ApiResponse.success(null);
    }

    @PostMapping("/orders")
    public ApiResponse<List<OrderTicketResponse>> orderTickets(@Valid @RequestBody OrderTicketRequest request) {
        return ApiResponse.success(ticketService.findOrderedTicketInfo(request.ticketIds()));
    }

    @PostMapping("/select/seat")
    public ApiResponse<TicketZonesResponse> selectZone(@Valid @RequestBody TicketZoneRequest request) {
        return ApiResponse.success(ticketService.getTicketZone(request));
    }

    @PostMapping("/hold/validation")
    public ResponseEntity<Void> validateTicketHold(@Valid @RequestBody ValidateTicketHoldRequest request) {
        validateTicketHoldService.validateTicketHold(request);
        return ResponseEntity.noContent().build();
    }
}
