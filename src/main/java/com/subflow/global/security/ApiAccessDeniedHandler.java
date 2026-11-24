package com.subflow.global.security;

import com.subflow.global.audit.AuditEventType;
import com.subflow.global.audit.AuditOutcome;
import com.subflow.global.audit.AuditService;
import com.subflow.global.exception.ErrorCode;
import com.subflow.global.exception.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.csrf.CsrfException;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;

/**
 * API 요청이 거부되었을 때 JSON 403을 반환한다.
 *
 * <p>CSRF 거부 감사는 IP당 분당 1회로 제한해 비인증 POST 폭주 시 DB 쓰기 증폭을 막는다.</p>
 */
@Slf4j
public class ApiAccessDeniedHandler implements AccessDeniedHandler {

    private static final Duration AUDIT_WINDOW = Duration.ofMinutes(1);

    private final ObjectMapper objectMapper;
    private final AuditService auditService;
    private final SlidingWindowLimiter auditLimiter;
    private final ClientIpResolver clientIpResolver;

    public ApiAccessDeniedHandler(ObjectMapper objectMapper, AuditService auditService) {
        this(objectMapper, auditService, new SlidingWindowLimiter(Clock.systemUTC()),
                new ClientIpResolver(false));
    }

    public ApiAccessDeniedHandler(ObjectMapper objectMapper,
                                  AuditService auditService,
                                  SlidingWindowLimiter auditLimiter,
                                  ClientIpResolver clientIpResolver) {
        this.objectMapper = objectMapper;
        this.auditService = auditService;
        this.auditLimiter = auditLimiter;
        this.clientIpResolver = clientIpResolver;
    }

    @Override
    public void handle(HttpServletRequest request,
                       HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        if (accessDeniedException instanceof CsrfException) {
            recordCsrfRejection(request, accessDeniedException);
        }
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE + ";charset=" + StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getWriter(), ErrorResponse.of(ErrorCode.FORBIDDEN));
    }

    private void recordCsrfRejection(HttpServletRequest request, AccessDeniedException accessDeniedException) {
        try {
            String ip = clientIpResolver.resolve(request);
            if (!auditLimiter.tryAcquire("csrf-rejected:ip:" + ip, 1, AUDIT_WINDOW)) {
                return;
            }
            auditService.record(AuditEventType.CSRF_REJECTED, AuditOutcome.DENIED,
                    null, null, null, accessDeniedException.getClass().getSimpleName());
        } catch (RuntimeException e) {
            log.warn("CSRF 거부 감사 기록을 남기지 못했습니다: {}", e.getClass().getSimpleName());
        }
    }
}
