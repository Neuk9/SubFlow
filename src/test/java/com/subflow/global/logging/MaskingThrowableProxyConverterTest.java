package com.subflow.global.logging;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.LoggingEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MaskingThrowableProxyConverterTest {

    @Test
    @DisplayName("스택트레이스의 예외·원인 메시지에 포함된 이메일도 마스킹한다")
    void masksEmailsInThrowableMessages() {
        LoggerContext context = new LoggerContext();
        Logger logger = context.getLogger("test");
        LoggingEvent event = new LoggingEvent(
                "test", logger, Level.ERROR, "failed for user@example.com",
                new IllegalStateException("customer admin@example.com not found",
                        new IllegalArgumentException("nested owner@example.org")),
                null);

        MaskingThrowableProxyConverter converter = new MaskingThrowableProxyConverter();
        converter.setContext(context);
        converter.start();

        String rendered = converter.convert(event);

        assertThat(rendered)
                .contains("IllegalStateException")
                .contains("a***@example.com")
                .contains("IllegalArgumentException")
                .contains("o***@example.org")
                .doesNotContain("admin@example.com")
                .doesNotContain("owner@example.org")
                .doesNotContain("user@example.com");
    }
}
