package com.programmers.kdt.ticket.domain.entity;

import com.programmers.kdt.common.entity.BaseTimeEntity;
import com.programmers.kdt.common.exception.BusinessException;
import com.programmers.kdt.ticket.domain.exception.TicketErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.ColumnDefault;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor
@Table(
        name = "ticket",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_performance_session_seat",
                        columnNames = {"performance_id", "session_num", "zone", "seat_row", "seat_num"}
                )
        }
)
public class Ticket extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long ticketId;

    @Column(name = "performance_id", nullable = false)
    private Long performanceId;

    @Column(name = "session_num", nullable = false)
    private Long sessionNum;

    @Column(name = "zone", nullable = false)
    private String zone;

    @Column(name = "seat_row", nullable = false)
    private String seatRow;

    @Column(name = "seat_num", nullable = false)
    private String seatNum;

    @Column(name = "price", nullable = false)
    private Long price;

    @Enumerated(EnumType.STRING)
    @Column(name = "ticket_status", nullable = false)
    @ColumnDefault("'AVAILABLE'")
    private TicketStatus ticketStatus = TicketStatus.AVAILABLE;

    @Column(name = "buy_user_id")
    private Long buyUserId;

    @Column(name = "hold_expired_at")
    private LocalDateTime holdExpiredAt;

    @Column(name = "standby_user_id")
    private Long standbyUserId;

    @Column(name = "standby_expired_at")
    private LocalDateTime standbyExpiredAt;

    @Column(name = "hold_key")
    private String holdKey;

    public static Ticket create(Long performanceId, Long sessionNum, String zone, String seatRow, String seatNum, Long price) {
        Ticket ticket = new Ticket();
        ticket.performanceId = performanceId;
        ticket.sessionNum = sessionNum;
        ticket.zone = zone;
        ticket.seatRow = seatRow;
        ticket.seatNum = seatNum;
        ticket.price = price;
        return ticket;
    }

    public void holdTicket (Long userId, LocalDateTime holdExpiredAt, String holdKey) {
        this.buyUserId = userId;
        this.holdExpiredAt = holdExpiredAt;
        this.holdKey = holdKey;
        this.ticketStatus = TicketStatus.HOLD;
    }

    public void standbyTicket(Long standbyUserId, LocalDateTime standbyExpiredAt) {
        this.ticketStatus = TicketStatus.CANCELED;
        this.standbyUserId = standbyUserId;
        this.standbyExpiredAt = standbyExpiredAt;
    }

    public void releaseToAvailable() {
        this.ticketStatus = TicketStatus.AVAILABLE;
        this.buyUserId = null;
        this.standbyUserId = null;
    }

    public void reservedTicket(Long buyUserId) {
        this.ticketStatus = TicketStatus.RESERVED;
        this.buyUserId = buyUserId;
    }

    public void validateHoldStatus() {
        if (this.ticketStatus != TicketStatus.HOLD) {
            throw new BusinessException(TicketErrorCode.TICKET_IS_NOT_HELD);
        }
    }

    public void validateAvailableStatus() {
        if (this.ticketStatus != TicketStatus.AVAILABLE) {
            throw new BusinessException(TicketErrorCode.IMPOSSIBLE_HOLD_TICKET);
        }
    }

    public void validateStandbyStatus(Long userId) {
        boolean isStandbyTicket = this.ticketStatus == TicketStatus.CANCELED
                && userId.equals(this.standbyUserId)
                && LocalDateTime.now().isBefore(this.standbyExpiredAt);

        if (!isStandbyTicket) {
            throw new BusinessException(TicketErrorCode.STANDBY_TICKET_DISCREPANCY);
        }
    }

    public void validateReservedStatus(Long userId) {
        if (this.ticketStatus != TicketStatus.RESERVED) {
            throw new BusinessException(TicketErrorCode.NOT_RESERVED_TICKET);
        }

        if (!userId.equals(this.buyUserId)) {
            throw new BusinessException(TicketErrorCode.TICKET_OWNER_DISCREPANCY);
        }
    }
}
