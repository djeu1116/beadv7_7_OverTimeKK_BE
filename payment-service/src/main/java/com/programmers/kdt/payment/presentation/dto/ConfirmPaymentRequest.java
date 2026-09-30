package com.programmers.kdt.payment.presentation.dto;

import jakarta.validation.constraints.NotBlank;

public record ConfirmPaymentRequest(
        @NotBlank String transactionKey
) {
}
