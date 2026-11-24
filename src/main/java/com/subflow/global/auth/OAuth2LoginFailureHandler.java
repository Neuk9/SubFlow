package com.subflow.global.auth;

import com.subflow.global.audit.AuditEventType;
import com.subflow.global.audit.AuditOutcome;
import com.subflow.global.audit.AuditService;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * 로그인 실패를 best-effort로 감사 기록하고 로그인 페이지로 돌려보낸다.
 * 감사 저장이 실패해도 로그인 실패 흐름은 유지한다.
 */
@Slf4j
@Component
public class OAuth2LoginFailureHandler extends SimpleUrlAuthenticationFailureHandler {

    private final AuditService auditService;

    public OAuth2LoginFailureHandler(AuditService auditService) {
        super("/login?error");
        this.auditService = auditService;
    }

    @Override
    public void onAuthenticationFailure(HttpServletRequest request,
                                        HttpServletResponse response,
                                        AuthenticationException exception) throws IOException, ServletException {
        try {
            auditService.record(AuditEventType.LOGIN_FAILURE, AuditOutcome.FAILURE,
                    null, "user", null, exception.getClass().getSimpleName());
        } catch (RuntimeException e) {
            log.warn("로그인 실패 감사 기록을 남기지 못했습니다: {}", e.getClass().getSimpleName());
        }
        super.onAuthenticationFailure(request, response, exception);
    }
}
