package com.programmers.kdt.standby.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Map;

// slack.alert.webhook-url 미설정 시(로컬 등) 조용히 스킵 - 알림 실패가 스케줄러 자체를 막으면 안 됨.
@Slf4j
@Component
public class SlackAlertClient {

    private final RestClient restClient;
    private final String webhookUrl;

    public SlackAlertClient(@Value("${slack.alert.webhook-url:}") String webhookUrl) {
        this.webhookUrl = webhookUrl;
        this.restClient = RestClient.create();
    }

    public void sendAlert(String message) {
        if (webhookUrl.isBlank()) {
            log.debug("Slack webhook 미설정 - 알림 스킵. message={}", message);
            return;
        }
        try {
            restClient.post()
                    .uri(webhookUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("text", message))
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception e) {
            log.warn("Slack 알림 발송 실패. message={}", message, e);
        }
    }
}
