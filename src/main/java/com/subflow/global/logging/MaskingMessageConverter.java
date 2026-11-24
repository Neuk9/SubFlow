package com.subflow.global.logging;

import ch.qos.logback.classic.pattern.ClassicConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;

/**
 * 로그 메시지에 포함된 이메일을 방어적으로 한 번 더 마스킹한다.
 */
public class MaskingMessageConverter extends ClassicConverter {

    @Override
    public String convert(ILoggingEvent event) {
        String message = event.getFormattedMessage();
        return message == null ? "" : PiiMasker.maskText(message);
    }
}
