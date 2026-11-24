package com.subflow.global.security;

import com.subflow.global.audit.AuditEventType;
import com.subflow.global.audit.AuditOutcome;
import com.subflow.global.audit.AuditService;
import com.subflow.global.auth.CurrentUserProvider;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class RequestThrottleFilterTest {

    @Mock
    private SlidingWindowLimiter limiter;

    @Mock
    private SlidingWindowLimiter auditLimiter;

    @Mock
    private ClientIpResolver clientIpResolver;

    @Mock
    private CurrentUserProvider currentUserProvider;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private AuditService auditService;

    @Mock
    private FilterChain filterChain;

    private RequestThrottleFilter filter;

    @BeforeEach
    void setUp() {
        filter = new RequestThrottleFilter(true, limiter, auditLimiter, clientIpResolver,
                currentUserProvider, objectMapper, auditService);
        given(currentUserProvider.getCurrentUserEmail()).willReturn("a@b.com");
    }

    @Test
    @DisplayName("같은 규칙·키의 반복 거부는 윈도우당 감사 기록을 한 번만 남긴다")
    void recordsRateLimitAuditOncePerWindow() throws Exception {
        given(limiter.tryAcquire(anyString(), anyInt(), any(Duration.class))).willReturn(false);
        given(auditLimiter.tryAcquire(anyString(), eq(1), any(Duration.class))).willReturn(true, false);

        filter.doFilter(request(), new MockHttpServletResponse(), filterChain);
        filter.doFilter(request(), new MockHttpServletResponse(), filterChain);

        then(auditService).should(times(1)).record(
                eq(AuditEventType.RATE_LIMITED), eq(AuditOutcome.DENIED),
                eq("a@b.com"), isNull(), isNull(), eq("data-export"));
    }

    @Test
    @DisplayName("감사 기록이 실패해도 429 응답은 그대로 반환한다")
    void auditFailureDoesNotBlockRejection() throws Exception {
        given(limiter.tryAcquire(anyString(), anyInt(), any(Duration.class))).willReturn(false);
        given(auditLimiter.tryAcquire(anyString(), eq(1), any(Duration.class))).willReturn(true);
        willThrow(new IllegalStateException("db down"))
                .given(auditService)
                .record(any(), any(), any(), any(), any(), any());

        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request(), response, filterChain);

        assertThat(response.getStatus()).isEqualTo(429);
        then(auditService).should(times(1)).record(
                any(), any(), any(), any(), any(), any());
    }

    private MockHttpServletRequest request() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/me/data");
        request.setRemoteAddr("1.2.3.4");
        return request;
    }
}
