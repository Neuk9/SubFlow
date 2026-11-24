package com.subflow.global.auth;

import com.subflow.global.audit.AuditEventType;
import com.subflow.global.audit.AuditOutcome;
import com.subflow.global.audit.AuditService;
import com.subflow.global.security.ClientIpResolver;
import com.subflow.global.security.SecurityProperties;
import com.subflow.global.security.SlidingWindowLimiter;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;

/**
 * 로그인 실패를 best-effort로 감사 기록하고 로그인 페이지로 돌려보낸다.
 * 감사 저장은 IP당 분당 1회로 제한해 비인증 콜백 폭주 시 DB 쓰기 증폭을 막고,
 * 감사 저장이 실패해도 로그인 실패 흐름은 유지한다.
 */
@Slf4j
@Component
public class OAuth2LoginFailureHandler extends SimpleUrlAuthenticationFailureHandler {

    private static final Duration AUDIT_WINDOW = Duration.ofMinutes(1);

    private final AuditService auditService;
    private final SlidingWindowLimiter auditLimiter;
    private final ClientIpResolver clientIpResolver;

    @Autowired
    public OAuth2LoginFailureHandler(AuditService auditService,
                                     SecurityProperties securityProperties,
                                     Clock clock) {
        this(auditService, new SlidingWindowLimiter(clock),
                new ClientIpResolver(securityProperties.trustCloudflareHeaders()));
    }

    OAuth2LoginFailureHandler(AuditService auditService,
                              SlidingWindowLimiter auditLimiter,
                              ClientIpResolver clientIpResolver) {
        super("/login?error");
        this.auditService = auditService;
        this.auditLimiter = auditLimiter;
        this.clientIpResolver = clientIpResolver;
    }

    @Override
    public void onAuthenticationFailure(HttpServletRequest request,
                                        HttpServletResponse response,
                                        AuthenticationException exception) throws IOException, ServletException {
        try {
            String ip = clientIpResolver.resolve(request);
            if (auditLimiter.tryAcquire("login-failure:ip:" + ip, 1, AUDIT_WINDOW)) {
                auditService.record(AuditEventType.LOGIN_FAILURE, AuditOutcome.FAILURE,
                        null, "user", null, exception.getClass().getSimpleName());
            }
        } catch (RuntimeException e) {
            log.warn("로그인 실패 감사 기록을 남기지 못했습니다: {}", e.getClass().getSimpleName());
        }
        super.onAuthenticationFailure(request, response, exception);
    }
}
