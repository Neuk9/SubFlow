package com.subflow.global.security;

import com.subflow.global.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class JsonBodySizeLimitFilterTest {

    private static final long MAX_BYTES = 1024L * 1024L;

    private final JsonBodySizeLimitFilter filter = new JsonBodySizeLimitFilter(MAX_BYTES);

    @Test
    @DisplayName("Content-Length가 1MB를 넘는 JSON 요청은 413으로 거부한다")
    void rejectsOversizedJsonByContentLength() throws Exception {
        MockHttpServletRequest request = jsonRequest("/api/imports/confirm", new byte[(int) MAX_BYTES + 1]);
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean invoked = new AtomicBoolean(false);

        filter.doFilter(request, response, (req, res) -> invoked.set(true));

        assertThat(invoked).isFalse();
        assertThat(response.getStatus()).isEqualTo(413);
        assertThat(response.getContentType()).startsWith(MediaType.APPLICATION_JSON_VALUE);
        assertThat(response.getContentAsString()).contains("\"code\":\"REQUEST_TOO_LARGE\"");
    }

    @Test
    @DisplayName("application/*+json 본문이 1MB를 넘으면 413으로 거부한다")
    void rejectsOversizedVendorJsonByContentLength() throws Exception {
        MockHttpServletRequest request = jsonRequest("/api/subscriptions/1",
                "application/merge-patch+json;charset=UTF-8", new byte[(int) MAX_BYTES + 1]);
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean invoked = new AtomicBoolean(false);

        filter.doFilter(request, response, (req, res) -> invoked.set(true));

        assertThat(invoked).isFalse();
        assertThat(response.getStatus()).isEqualTo(413);
        assertThat(response.getContentAsString()).contains("\"code\":\"REQUEST_TOO_LARGE\"");
    }

    @Test
    @DisplayName("vnd.api+json 본문이 1MB를 넘으면 413으로 거부한다")
    void rejectsOversizedVndApiJsonByContentLength() throws Exception {
        MockHttpServletRequest request = jsonRequest("/api/subscriptions/1",
                "application/vnd.api+json", new byte[(int) MAX_BYTES + 1]);
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean invoked = new AtomicBoolean(false);

        filter.doFilter(request, response, (req, res) -> invoked.set(true));

        assertThat(invoked).isFalse();
        assertThat(response.getStatus()).isEqualTo(413);
        assertThat(response.getContentAsString()).contains("\"code\":\"REQUEST_TOO_LARGE\"");
    }

    @Test
    @DisplayName("Content-Length가 정확히 1MB인 JSON 요청은 통과한다")
    void allowsJsonAtLimit() throws Exception {
        MockHttpServletRequest request = jsonRequest("/api/imports/confirm", new byte[(int) MAX_BYTES]);
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean invoked = new AtomicBoolean(false);

        filter.doFilter(request, response, (req, res) -> invoked.set(true));

        assertThat(invoked).isTrue();
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("Content-Length가 없는 JSON 요청도 1MB를 넘으면 413으로 거부한다")
    void rejectsOversizedChunkedJson() throws Exception {
        MockHttpServletRequest request = chunkedRequest("/api/imports/confirm", new byte[(int) MAX_BYTES + 1]);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> req.getInputStream().readAllBytes());

        assertThat(response.getStatus()).isEqualTo(413);
        assertThat(response.getContentAsString()).contains("\"code\":\"REQUEST_TOO_LARGE\"");
    }

    @Test
    @DisplayName("Content-Length가 없는 1MB 이하 JSON 요청은 통과한다")
    void allowsChunkedJsonUnderLimit() throws Exception {
        MockHttpServletRequest request = chunkedRequest("/api/imports/confirm", new byte[1024]);
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean invoked = new AtomicBoolean(false);

        filter.doFilter(request, response, (req, res) -> {
            req.getInputStream().readAllBytes();
            invoked.set(true);
        });

        assertThat(invoked).isTrue();
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("JSON이 아닌 요청은 크기를 검사하지 않는다")
    void ignoresNonJsonRequests() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/imports/preview");
        request.setContentType(MediaType.MULTIPART_FORM_DATA_VALUE);
        request.setContent(new byte[(int) MAX_BYTES + 1]);
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean invoked = new AtomicBoolean(false);

        filter.doFilter(request, response, (req, res) -> invoked.set(true));

        assertThat(invoked).isTrue();
    }

    @Test
    @DisplayName("/api/ 밖의 JSON 요청은 크기를 검사하지 않는다")
    void ignoresNonApiPaths() throws Exception {
        MockHttpServletRequest request = jsonRequest("/h2-console/login", new byte[(int) MAX_BYTES + 1]);
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicBoolean invoked = new AtomicBoolean(false);

        filter.doFilter(request, response, (req, res) -> invoked.set(true));

        assertThat(invoked).isTrue();
    }

    @Test
    @DisplayName("제한 스트림 예외가 HttpMessageNotReadableException으로 감싸져도 413으로 매핑한다")
    void mapsWrappedBodyTooLargeExceptionToPayloadTooLarge() {
        HttpMessageNotReadableException wrapped = new HttpMessageNotReadableException(
                "I/O error while reading input message",
                new JsonBodySizeLimitFilter.BodyTooLargeException(),
                null);

        var response = new GlobalExceptionHandler().handleNotReadable(wrapped);

        assertThat(response.getStatusCode().value()).isEqualTo(413);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo("REQUEST_TOO_LARGE");
    }

    private MockHttpServletRequest jsonRequest(String uri, byte[] content) {
        return jsonRequest(uri, MediaType.APPLICATION_JSON_VALUE, content);
    }

    private MockHttpServletRequest jsonRequest(String uri, String contentType, byte[] content) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", uri);
        request.setContentType(contentType);
        request.setContent(content);
        return request;
    }

    private MockHttpServletRequest chunkedRequest(String uri, byte[] content) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", uri) {
            @Override
            public long getContentLengthLong() {
                return -1L;
            }

            @Override
            public int getContentLength() {
                return -1;
            }
        };
        request.setContentType(MediaType.APPLICATION_JSON_VALUE);
        request.setContent(content);
        return request;
    }
}
