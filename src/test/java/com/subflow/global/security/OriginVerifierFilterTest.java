package com.subflow.global.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class OriginVerifierFilterTest {

    private final OriginVerifierFilter filter = new OriginVerifierFilter(List.of(
            "http://localhost:8080",
            "https://subflow.stda.app"
    ));

    @Test
    @DisplayName("안전한 메서드는 Origin과 무관하게 통과한다")
    void allowsSafeMethods() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/subscriptions");
        request.addHeader("Origin", "https://evil.example");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean invoked = new AtomicBoolean(false);

        filter.doFilter(request, response, (req, res) -> invoked.set(true));

        assertThat(invoked).isTrue();
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("허용된 Origin의 변경 요청은 통과한다")
    void allowsAllowedOrigin() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/subscriptions");
        request.addHeader("Origin", "http://localhost:8080");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean invoked = new AtomicBoolean(false);

        filter.doFilter(request, response, (req, res) -> invoked.set(true));

        assertThat(invoked).isTrue();
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("기본 포트를 생략한 Origin도 허용 목록과 일치한다")
    void allowsAllowedOriginWithDefaultPort() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/subscriptions");
        request.addHeader("Origin", "https://subflow.stda.app");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean invoked = new AtomicBoolean(false);

        filter.doFilter(request, response, (req, res) -> invoked.set(true));

        assertThat(invoked).isTrue();
    }

    @Test
    @DisplayName("Origin도 Referer도 없으면 통과한다")
    void allowsMissingOriginAndReferer() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/subscriptions");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean invoked = new AtomicBoolean(false);

        filter.doFilter(request, response, (req, res) -> invoked.set(true));

        assertThat(invoked).isTrue();
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("허용되지 않은 Origin의 변경 요청은 403 JSON으로 거부된다")
    void rejectsDisallowedOrigin() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/subscriptions");
        request.addHeader("Origin", "https://evil.example");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean invoked = new AtomicBoolean(false);

        filter.doFilter(request, response, (req, res) -> invoked.set(true));

        assertThat(invoked).isFalse();
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentType()).startsWith("application/json");
        assertThat(response.getContentAsString()).contains("\"code\":\"ORIGIN_NOT_ALLOWED\"");
    }

    @Test
    @DisplayName("null 문자열 Origin은 불일치로 본다")
    void rejectsNullOrigin() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/subscriptions");
        request.addHeader("Origin", "null");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean invoked = new AtomicBoolean(false);

        filter.doFilter(request, response, (req, res) -> invoked.set(true));

        assertThat(invoked).isFalse();
        assertThat(response.getStatus()).isEqualTo(403);
    }

    @Test
    @DisplayName("Origin이 없으면 허용된 Referer의 오리진으로 판단한다")
    void allowsAllowedReferer() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/subscriptions");
        request.addHeader("Referer", "https://subflow.stda.app/settings");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean invoked = new AtomicBoolean(false);

        filter.doFilter(request, response, (req, res) -> invoked.set(true));

        assertThat(invoked).isTrue();
    }

    @Test
    @DisplayName("Origin이 없고 Referer가 허용되지 않으면 403으로 거부된다")
    void rejectsDisallowedReferer() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/subscriptions");
        request.addHeader("Referer", "https://evil.example/settings");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean invoked = new AtomicBoolean(false);

        filter.doFilter(request, response, (req, res) -> invoked.set(true));

        assertThat(invoked).isFalse();
        assertThat(response.getStatus()).isEqualTo(403);
    }
}
