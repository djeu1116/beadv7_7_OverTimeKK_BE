package com.programmers.kdt.order.application.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * user-service를 REST로 호출하는 클라이언트
 * MSA에서 다른 서비스 데이터가 필요할 때, DB를 직접 조회하지 않고 이렇게 네트워크로 요청합니다.
 */
@Component
public class UserClient {

    private final RestClient restClient;

    public UserClient(@Value("${user-service.url}") String userServiceUrl) {
        this.restClient = RestClient.create(userServiceUrl);
    }

    public boolean existsUser(Long userId) {
        // TODO: GET /api/users/{userId} 호출해서 존재 여부 확인
        return true;
    }
}
