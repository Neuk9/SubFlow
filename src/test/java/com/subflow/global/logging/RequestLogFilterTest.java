package com.subflow.global.logging;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RequestLogFilterTest {

    private final RequestLogFilter filter = new RequestLogFilter();

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    @DisplayName("요청마다 UUID를 생성해 헤더·속성·MDC에 넣고 종료 후 MDC를 비운다")
    void setsRequestIdAndClearsMdc() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/subscriptions");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> mdcDuringChain = new AtomicReference<>();
        AtomicReference<String> attributeDuringChain = new AtomicReference<>();

        filter.doFilter(request, response, (req, res) -> {
            mdcDuringChain.set(MDC.get("requestId"));
            attributeDuringChain.set((String) req.getAttribute("requestId"));
        });

        String requestId = response.getHeader("X-Request-Id");
        assertThat(requestId).isNotNull();
        assertThat(requestId).hasSize(36);
        assertThat(UUID.fromString(requestId).toString()).isEqualTo(requestId);
        assertThat(mdcDuringChain.get()).isEqualTo(requestId);
        assertThat(attributeDuringChain.get()).isEqualTo(requestId);
        assertThat(request.getAttribute("requestId")).isEqualTo(requestId);
        assertThat(MDC.get("requestId")).isNull();
    }

    @Test
    @DisplayName("쿼리스트링 없이 메서드·경로·상태·소요시간만 기록한다")
    void logsWithoutQueryString() throws Exception {
        Logger logger = (Logger) LoggerFactory.getLogger(RequestLogFilter.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/subscriptions");
            request.setQueryString("email=user@example.com&token=secret");
            MockHttpServletResponse response = new MockHttpServletResponse();
            response.setStatus(204);

            filter.doFilter(request, response, (req, res) -> {
            });

            assertThat(appender.list).anySatisfy(event -> {
                String message = event.getFormattedMessage();
                assertThat(message).contains("GET")
                        .contains("/api/subscriptions")
                        .contains("204")
                        .contains("ms")
                        .doesNotContain("email=")
                        .doesNotContain("user@example.com")
                        .doesNotContain("token=secret")
                        .doesNotContain(request.getQueryString());
            });
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    @Test
    @DisplayName("다음 필터가 예외를 던져도 MDC를 비운다")
    void clearsMdcWhenChainThrows() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/imports/confirm");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThatThrownBy(() -> filter.doFilter(request, response,
                (req, res) -> {
                    throw new ServletException("boom");
                }))
                .isInstanceOf(ServletException.class);

        assertThat(MDC.get("requestId")).isNull();
        assertThat(response.getHeader("X-Request-Id")).isNotBlank();
    }
}
