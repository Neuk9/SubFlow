package com.subflow.global.logging;

import org.springframework.boot.web.error.ErrorAttributeOptions;
import org.springframework.boot.webmvc.error.DefaultErrorAttributes;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.WebRequest;

import java.util.Map;

/**
 * HTML 오류 페이지({@code error.mustache})에서 서버 생성 {@code requestId}를 표시할 수 있도록
 * 기본 오류 속성에 {@code requestId}만 선별적으로 노출한다.
 */
@Component
public class SubflowErrorAttributes extends DefaultErrorAttributes {

    @Override
    public Map<String, Object> getErrorAttributes(WebRequest webRequest, ErrorAttributeOptions options) {
        Map<String, Object> attributes = super.getErrorAttributes(webRequest, options);
        Object requestId = webRequest.getAttribute(
                RequestLogFilter.REQUEST_ID_ATTRIBUTE, RequestAttributes.SCOPE_REQUEST);
        if (requestId instanceof String value && !value.isBlank()) {
            attributes.put(RequestLogFilter.REQUEST_ID_ATTRIBUTE, value);
        }
        return attributes;
    }
}
