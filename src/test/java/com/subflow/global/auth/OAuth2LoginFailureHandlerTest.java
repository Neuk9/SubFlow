package com.subflow.global.auth;

import com.subflow.global.audit.AuditEventType;
import com.subflow.global.audit.AuditOutcome;
import com.subflow.global.audit.AuditService;
import com.subflow.global.security.ClientIpResolver;
import com.subflow.global.security.SlidingWindowLimiter;
import com.subflow.support.MutableClock;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class OAuth2LoginFailureHandlerTest {

    @Mock
    private AuditService auditService;

    @Test
    @DisplayName("같은 IP의 반복 로그인 실패는 분당 1회만 감사 기록한다")
    void throttlesLoginFailureAuditPerIp() throws Exception {
        MutableClock clock = new MutableClock(Instant.parse("2026-11-25T00:00:00Z"));
        OAuth2LoginFailureHandler handler = new OAuth2LoginFailureHandler(
                auditService, new SlidingWindowLimiter(clock), new ClientIpResolver(false));

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/login/oauth2/code/google");
        request.setRemoteAddr("198.51.100.20");

        handler.onAuthenticationFailure(request, new MockHttpServletResponse(), new BadCredentialsException("bad"));
        handler.onAuthenticationFailure(request, new MockHttpServletResponse(), new BadCredentialsException("bad"));

        then(auditService).should(times(1)).record(
                eq(AuditEventType.LOGIN_FAILURE), eq(AuditOutcome.FAILURE),
                isNull(), eq("user"), isNull(), eq("BadCredentialsException"));

        clock.plus(Duration.ofMinutes(1));
        handler.onAuthenticationFailure(request, new MockHttpServletResponse(), new BadCredentialsException("bad"));

        then(auditService).should(times(2)).record(
                eq(AuditEventType.LOGIN_FAILURE), eq(AuditOutcome.FAILURE),
                isNull(), eq("user"), isNull(), eq("BadCredentialsException"));
    }

    @Test
    @DisplayName("감사 기록이 실패해도 로그인 오류 페이지로 리다이렉트한다")
    void redirectsEvenWhenAuditFails() throws Exception {
        MutableClock clock = new MutableClock(Instant.parse("2026-11-25T00:00:00Z"));
        OAuth2LoginFailureHandler handler = new OAuth2LoginFailureHandler(
                auditService, new SlidingWindowLimiter(clock), new ClientIpResolver(false));
        willThrow(new IllegalStateException("db down"))
                .given(auditService).record(any(), any(), any(), any(), any(), any());

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/login/oauth2/code/google");
        request.setRemoteAddr("198.51.100.21");
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationFailure(request, response, new BadCredentialsException("bad"));

        assertThat(response.getRedirectedUrl()).isEqualTo("/login?error");
    }
}
