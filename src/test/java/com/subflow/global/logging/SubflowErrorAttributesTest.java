package com.subflow.global.logging;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.error.ErrorAttributeOptions;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.ServletWebRequest;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SubflowErrorAttributesTest {

    private final SubflowErrorAttributes errorAttributes = new SubflowErrorAttributes();

    @Test
    @DisplayName("요청 속성에 requestId가 있으면 오류 모델에 포함한다")
    void includesRequestIdWhenPresent() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/error");
        request.setAttribute(RequestLogFilter.REQUEST_ID_ATTRIBUTE, "req-1234");

        Map<String, Object> model = errorAttributes.getErrorAttributes(
                new ServletWebRequest(request), ErrorAttributeOptions.defaults());

        assertThat(model).containsEntry("requestId", "req-1234");
    }

    @Test
    @DisplayName("요청 속성에 requestId가 없으면 모델에 넣지 않는다")
    void omitsRequestIdWhenAbsent() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/error");

        Map<String, Object> model = errorAttributes.getErrorAttributes(
                new ServletWebRequest(request), ErrorAttributeOptions.defaults());

        assertThat(model).doesNotContainKey("requestId");
    }
}
