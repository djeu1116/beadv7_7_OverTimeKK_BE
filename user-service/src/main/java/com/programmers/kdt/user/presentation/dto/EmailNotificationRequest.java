package com.programmers.kdt.user.presentation.dto;

import jakarta.validation.constraints.NotBlank;

public record EmailNotificationRequest (
    @NotBlank(message = "메일 제목은 필수입니다.")
    String subject,

    @NotBlank(message = "메일 본문은 필수입니다.")
    String body
) {
}
