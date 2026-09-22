package com.programmers.kdt.common.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

// 게이트웨이가 /api/** 만 라우팅해서 /internal/** 는 외부에 노출되지 않지만,
// 네트워크 경로가 하나라도 열리면 인증 없는 내부 API가 되므로 공유 토큰을 한 겹 더 둔다.
@Slf4j
@Component
public class InternalAuthFilter extends OncePerRequestFilter {

    public static final String INTERNAL_TOKEN_HEADER = "X-Internal-Token";

    private final String internalToken;

    public InternalAuthFilter(@Value("${internal.auth.token}") String internalToken) {
        this.internalToken = internalToken;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/internal/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (!internalToken.equals(request.getHeader(INTERNAL_TOKEN_HEADER))) {
            log.warn("내부 API 인증 실패 - uri={}, remoteAddr={}", request.getRequestURI(), request.getRemoteAddr());
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }
        filterChain.doFilter(request, response);
    }
}
