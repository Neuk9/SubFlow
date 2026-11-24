package com.subflow.global.logging;

import ch.qos.logback.classic.pattern.ThrowableProxyConverter;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.core.CoreConstants;

/**
 * 스택트레이스의 예외·원인 메시지에 포함된 이메일까지 마스킹한다.
 *
 * <p>메시지 컨버터({@link MaskingMessageConverter})는 포맷된 로그 메시지만 가리므로,
 * 5xx 로그처럼 throwable을 함께 남길 때는 이 컨버터가 필요하다.</p>
 */
public class MaskingThrowableProxyConverter extends ThrowableProxyConverter {

    @Override
    protected String throwableProxyToString(IThrowableProxy tp) {
        String rendered = CoreConstants.LINE_SEPARATOR
                + super.throwableProxyToString(tp)
                + CoreConstants.LINE_SEPARATOR;
        return PiiMasker.maskText(rendered);
    }
}
