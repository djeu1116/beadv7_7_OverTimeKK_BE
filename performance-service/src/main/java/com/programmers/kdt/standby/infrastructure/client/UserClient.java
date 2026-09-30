package com.programmers.kdt.standby.infrastructure.client;

import com.programmers.kdt.standby.presentation.dto.EmailNotificationRequest;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

@Component
public class UserClient {

    private final RestClient restClient;

    public UserClient(@Value("${user-service.url}") String userServiceUrl) {
        this.restClient = RestClient.create(userServiceUrl);
    }

    public void sendMatchNotification(Long userId, String subject, String body) {
        restClient.post()
                .uri("/api/users/{userId}/notifications/email", userId)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new EmailNotificationRequest(subject, body))
                .retrieve()
                .toBodilessEntity();
    }
}
