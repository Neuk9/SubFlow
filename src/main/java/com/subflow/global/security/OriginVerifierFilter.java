package com.subflow.global.security;

import com.subflow.global.exception.ErrorCode;
import com.subflow.global.exception.ErrorResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 변경 요청(POST/PUT/PATCH/DELETE)의 Origin(없으면 Referer)이 허용 목록에 있는지 확인한다.
 * 둘 다 없으면 CSRF 토큰이 1차 방어선이므로 통과시킨다.
 */
public class OriginVerifierFilter extends OncePerRequestFilter {

    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS", "TRACE");

    private final Set<String> allowedOrigins;
    private final ObjectMapper objectMapper;

    public OriginVerifierFilter(List<String> allowedOrigins) {
        this(allowedOrigins, new ObjectMapper());
    }

    public OriginVerifierFilter(List<String> allowedOrigins, ObjectMapper objectMapper) {
        this.allowedOrigins = new HashSet<>();
        if (allowedOrigins != null) {
            for (String origin : allowedOrigins) {
                String normalized = normalizeOrigin(origin);
                if (normalized != null) {
                    this.allowedOrigins.add(normalized);
                }
            }
        }
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (isSafeMethod(request.getMethod()) || isAllowedRequest(request)) {
            filterChain.doFilter(request, response);
            return;
        }
        reject(response);
    }

    private boolean isSafeMethod(String method) {
        return method != null && SAFE_METHODS.contains(method.toUpperCase(Locale.ROOT));
    }

    private boolean isAllowedRequest(HttpServletRequest request) {
        String origin = request.getHeader("Origin");
        if (origin != null) {
            return isAllowedOrigin(origin);
        }
        String referer = request.getHeader("Referer");
        if (referer != null) {
            return isAllowedOrigin(referer);
        }
        return true;
    }

    private boolean isAllowedOrigin(String value) {
        String normalized = normalizeOrigin(value);
        return normalized != null && allowedOrigins.contains(normalized);
    }

    private String normalizeOrigin(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            URI uri = URI.create(value.trim());
            String scheme = uri.getScheme();
            String host = uri.getHost();
            if (scheme == null || host == null) {
                return null;
            }
            scheme = scheme.toLowerCase(Locale.ROOT);
            if (!scheme.equals("http") && !scheme.equals("https")) {
                return null;
            }
            int port = uri.getPort();
            if (port == -1) {
                port = scheme.equals("https") ? 443 : 80;
            }
            return scheme + "://" + host.toLowerCase(Locale.ROOT) + ":" + port;
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private void reject(HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE + ";charset=" + StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getWriter(), ErrorResponse.of(ErrorCode.ORIGIN_NOT_ALLOWED));
    }
}
