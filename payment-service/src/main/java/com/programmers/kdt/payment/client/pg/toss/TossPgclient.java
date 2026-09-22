package com.programmers.kdt.payment.client.pg.toss;

import com.programmers.kdt.payment.client.pg.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.Map;

@Profile("toss")
@Component
public class TossPgclient implements PgClient {

    private final RestClient restClient;

    public TossPgclient(@Value("${toss.secret-key}") String secretKey) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(3));
        requestFactory.setReadTimeout(Duration.ofSeconds(30));

        String encoded = Base64.getEncoder().encodeToString(((secretKey) + ":").getBytes());
        this.restClient = RestClient.builder()
                .baseUrl("https://api.tosspayments.com")
                .requestFactory(requestFactory)
                .defaultHeader("Authorization", "Basic " + encoded)
                .build();
    }

    @Override
    public PgReadyResult ready(PgReadyCommand command) {
        String orderId = PgOrderIdFormatter.format(command.orderId());
        return new PgReadyResult(null, orderId, null);
    }

    @Override
    public PgApproveResult approve(PgApproveCommand command) {
        try {
            TossConfirmResponse response = restClient.post()
                    .uri("/v1/payments/confirm")
                    .body(Map.of(
                            "paymentKey", command.transactionKey(),
                            "orderId", command.orderId(),
                            "amount", command.amount()
                    ))
                    .retrieve()
                    .body(TossConfirmResponse.class);

            boolean success = "DONE".equals(response.status());
            LocalDateTime approvedAt = success
                    ? OffsetDateTime.parse(response.approvedAt()).toLocalDateTime()
                    : LocalDateTime.now();

            return new PgApproveResult(success, approvedAt);
        } catch (RestClientResponseException e) {
            TossErrorResponse error = e.getResponseBodyAs(TossErrorResponse.class);
            throw new PgClientException(error.code(), error.message());
        }
    }

    @Override
    public PgCancelResult cancel(PgCancelCommand command) {
        try {
            Map<String, Object> body = command.cancelAmount() != null
                    ? Map.of("cancelReason", command.reason(), "cancelAmount", command.cancelAmount())
                    : Map.of("cancelReason", command.reason());

            TossCancelResponse response = restClient.post()
                    .uri("/v1/payments/{paymentKey}/cancel", command.transactionKey())
                    .body(body)
                    .retrieve()
                    .body(TossCancelResponse.class);

            boolean success = response.status() != null && response.status().contains("CANCEL");

            LocalDateTime canceledAt = LocalDateTime.now();
            if (success && response.cancels() != null && !response.cancels().isEmpty()) {
                String latest = response.cancels().get(response.cancels().size() - 1).canceledAt();
                canceledAt = OffsetDateTime.parse(latest).toLocalDateTime();
            }

            return new PgCancelResult(success, canceledAt);
        } catch (RestClientResponseException e) {
            TossErrorResponse error = e.getResponseBodyAs(TossErrorResponse.class);
            throw new PgClientException(error.code(), error.message());
        }
    }

    @Override
    public PgApproveResult select(String paymentKey) {
        try {
            TossConfirmResponse response = restClient.get()
                    .uri("/v1/payments/{paymentKey}", paymentKey)
                    .retrieve()
                    .body(TossConfirmResponse.class);

            boolean success = "DONE".equals(response.status());
            LocalDateTime approvedAt = success
                    ? OffsetDateTime.parse(response.approvedAt()).toLocalDateTime()
                    : LocalDateTime.now();
            return new PgApproveResult(success, approvedAt);
        } catch (RestClientResponseException e) {
            TossErrorResponse error = e.getResponseBodyAs(TossErrorResponse.class);
            throw new PgClientException(error.code(), error.message());
        }
    }
}
