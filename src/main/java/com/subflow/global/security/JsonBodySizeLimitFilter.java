package com.subflow.global.security;

import com.subflow.global.exception.ErrorCode;
import com.subflow.global.exception.ErrorResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * {@code /api/**} 의 {@code application/json} 요청 본문을 1MB로 제한한다.
 * Content-Length가 있으면 즉시 검사하고, 없으면(chunked) 입력 스트림을 제한해
 * 1MB를 넘는 순간 {@link BodyTooLargeException}으로 읽기를 중단한다.
 */
public class JsonBodySizeLimitFilter extends OncePerRequestFilter {

    public static final long DEFAULT_MAX_BYTES = 1024L * 1024L;

    private static final String API_PREFIX = "/api/";
    private static final String JSON_CONTENT_TYPE = "application/json";

    private final long maxBytes;
    private final ObjectMapper objectMapper;

    public JsonBodySizeLimitFilter(long maxBytes) {
        this(maxBytes, new ObjectMapper());
    }

    public JsonBodySizeLimitFilter(long maxBytes, ObjectMapper objectMapper) {
        this.maxBytes = maxBytes;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (!appliesTo(request)) {
            filterChain.doFilter(request, response);
            return;
        }

        long contentLength = request.getContentLengthLong();
        if (contentLength > maxBytes) {
            reject(response);
            return;
        }
        if (contentLength >= 0) {
            filterChain.doFilter(request, response);
            return;
        }

        LimitedRequestWrapper wrapper = new LimitedRequestWrapper(request, maxBytes);
        try {
            filterChain.doFilter(wrapper, response);
        } catch (ServletException | IOException | RuntimeException e) {
            if (wrapper.isExceeded() && !response.isCommitted()) {
                reject(response);
                return;
            }
            throw e;
        }
    }

    private boolean appliesTo(HttpServletRequest request) {
        String uri = request.getRequestURI();
        if (uri == null || !uri.startsWith(API_PREFIX)) {
            return false;
        }
        String contentType = request.getContentType();
        if (contentType == null) {
            return false;
        }
        return contentType.trim().toLowerCase().startsWith(JSON_CONTENT_TYPE);
    }

    private void reject(HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.PAYLOAD_TOO_LARGE.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE + ";charset=" + StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getWriter(), ErrorResponse.of(ErrorCode.REQUEST_TOO_LARGE));
    }

    /**
     * Content-Length가 없는 요청의 입력 스트림을 1MB에서 끊는 래퍼.
     */
    private static final class LimitedRequestWrapper extends HttpServletRequestWrapper {

        private final long maxBytes;
        private LimitedServletInputStream inputStream;
        private boolean exceeded;

        private LimitedRequestWrapper(HttpServletRequest request, long maxBytes) {
            super(request);
            this.maxBytes = maxBytes;
        }

        @Override
        public ServletInputStream getInputStream() throws IOException {
            if (inputStream == null) {
                inputStream = new LimitedServletInputStream(super.getInputStream(), maxBytes, this);
            }
            return inputStream;
        }

        @Override
        public BufferedReader getReader() throws IOException {
            String encoding = getCharacterEncoding();
            Charset charset = encoding != null ? Charset.forName(encoding) : StandardCharsets.UTF_8;
            return new BufferedReader(new InputStreamReader(getInputStream(), charset));
        }

        private boolean isExceeded() {
            return exceeded;
        }

        private void markExceeded() {
            this.exceeded = true;
        }
    }

    /**
     * 최대 바이트를 넘는 순간 읽기를 중단하는 서블릿 입력 스트림.
     */
    private static final class LimitedServletInputStream extends ServletInputStream {

        private final ServletInputStream delegate;
        private final long maxBytes;
        private final LimitedRequestWrapper owner;
        private long count;

        private LimitedServletInputStream(ServletInputStream delegate, long maxBytes,
                                          LimitedRequestWrapper owner) {
            this.delegate = delegate;
            this.maxBytes = maxBytes;
            this.owner = owner;
        }

        @Override
        public int read() throws IOException {
            int value = delegate.read();
            if (value != -1) {
                count++;
                checkLimit();
            }
            return value;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            if (len == 0) {
                return 0;
            }
            long remaining = maxBytes - count;
            int allowed = (int) Math.min(len, remaining + 1);
            int read = delegate.read(b, off, allowed);
            if (read > 0) {
                count += read;
                checkLimit();
            }
            return read;
        }

        private void checkLimit() throws BodyTooLargeException {
            if (count > maxBytes) {
                owner.markExceeded();
                throw new BodyTooLargeException();
            }
        }

        @Override
        public boolean isFinished() {
            return delegate.isFinished();
        }

        @Override
        public boolean isReady() {
            return delegate.isReady();
        }

        @Override
        public void setReadListener(ReadListener readListener) {
            delegate.setReadListener(readListener);
        }
    }

    /**
     * JSON 본문이 허용 크기를 넘었음을 나타낸다.
     * {@code @RequestBody} 변환 과정에서 {@code HttpMessageNotReadableException}으로
     * 감싸질 수 있으므로 {@code GlobalExceptionHandler}가 원인 체인에서 찾아 413으로 매핑한다.
     */
    public static class BodyTooLargeException extends IOException {

        public BodyTooLargeException() {
            super("JSON request body exceeds the allowed size");
        }
    }
}
