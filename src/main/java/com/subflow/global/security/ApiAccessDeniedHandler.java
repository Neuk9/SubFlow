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
import org.springframework.security.web.csrf.InvalidCsrfTokenException;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * API 요청이 거부되었을 때 JSON 403을 반환한다.
 */
@Slf4j
public class ApiAccessDeniedHandler implements AccessDeniedHandler {

    private final ObjectMapper objectMapper;
    private final AuditService auditService;

    public ApiAccessDeniedHandler(ObjectMapper objectMapper, AuditService auditService) {
        this.objectMapper = objectMapper;
        this.auditService = auditService;
    }

    @Override
    public void handle(HttpServletRequest request,
                       HttpServletResponse response,
                       AccessDeniedException accessDeniedException) throws IOException {
        if (accessDeniedException instanceof InvalidCsrfTokenException) {
            recordCsrfRejection();
        }
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE + ";charset=" + StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getWriter(), ErrorResponse.of(ErrorCode.FORBIDDEN));
    }

    private void recordCsrfRejection() {
        try {
            auditService.record(AuditEventType.CSRF_REJECTED, AuditOutcome.DENIED,
                    null, null, null, "invalid-csrf-token");
        } catch (RuntimeException e) {
            log.warn("CSRF 거부 감사 기록을 남기지 못했습니다: {}", e.getClass().getSimpleName());
        }
    }
}
