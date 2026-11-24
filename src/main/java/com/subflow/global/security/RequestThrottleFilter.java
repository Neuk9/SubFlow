package com.subflow.global.security;

import com.subflow.global.audit.AuditEventType;
import com.subflow.global.audit.AuditOutcome;
import com.subflow.global.audit.AuditService;
import com.subflow.global.auth.CurrentUserProvider;
import com.subflow.global.exception.ErrorCode;
import com.subflow.global.exception.ErrorResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

/**
 * 경로별 슬라이딩 윈도우 요청 제한을 적용한다.
 *
 * <p>인증 사용자는 이메일, 비인증 요청은 클라이언트 IP를 키로 사용한다.
 * 규칙에 매칭되지 않는 요청에도 IP 기준 전체 안전판을 적용한다.
 * 한도를 넘으면 429와 {@code Retry-After}(초)를 포함한 JSON을 반환한다.</p>
 */
@Slf4j
public class RequestThrottleFilter extends OncePerRequestFilter {

    private static final Duration ONE_MINUTE = Duration.ofMinutes(1);
    private static final Duration ONE_HOUR = Duration.ofHours(1);

    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    private static final String API_PREFIX = "/api/";
    private static final String API_GLOB = "/api/**";

    private static final Rule SAFETY_NET =
            new Rule("global", null, null, KeyType.IP, 1_000, ONE_MINUTE);

    private static final List<Rule> RULES = List.of(
            new Rule("import-preview", "POST", "/api/imports/preview", KeyType.USER, 10, ONE_MINUTE),
            new Rule("import-confirm", "POST", "/api/imports/confirm", KeyType.USER, 10, ONE_MINUTE),
            new Rule("data-export", "GET", "/api/me/data", KeyType.USER, 3, ONE_MINUTE),
            new Rule("account-delete", "DELETE", "/api/me", KeyType.USER, 3, ONE_HOUR),
            new Rule("subscription-share", "POST", "/api/subscriptions/*/shares", KeyType.USER, 20, ONE_HOUR),
            new Rule("share-reinvite", "POST", "/api/shares/*/reinvite", KeyType.USER, 20, ONE_HOUR),
            new Rule("share-accept", "POST", "/api/shares/*/accept", KeyType.USER, 30, ONE_MINUTE),
            new Rule("share-decline", "POST", "/api/shares/*/decline", KeyType.USER, 30, ONE_MINUTE),
            new Rule("google-login", null, "/oauth2/authorization/google", KeyType.IP, 20, ONE_MINUTE),
            new Rule("login-page", null, "/login", KeyType.IP, 20, ONE_MINUTE),
            new Rule("api-default", null, API_GLOB, KeyType.USER, 300, ONE_MINUTE)
    );

    private final boolean enabled;
    private final SlidingWindowLimiter limiter;
    private final SlidingWindowLimiter auditLimiter;
    private final ClientIpResolver clientIpResolver;
    private final CurrentUserProvider currentUserProvider;
    private final ObjectMapper objectMapper;
    private final AuditService auditService;

    public RequestThrottleFilter(boolean enabled,
                                 SlidingWindowLimiter limiter,
                                 SlidingWindowLimiter auditLimiter,
                                 ClientIpResolver clientIpResolver,
                                 CurrentUserProvider currentUserProvider,
                                 ObjectMapper objectMapper,
                                 AuditService auditService) {
        this.enabled = enabled;
        this.limiter = limiter;
        this.auditLimiter = auditLimiter;
        this.clientIpResolver = clientIpResolver;
        this.currentUserProvider = currentUserProvider;
        this.objectMapper = objectMapper;
        this.auditService = auditService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (!enabled) {
            filterChain.doFilter(request, response);
            return;
        }

        Rule rule = findRule(request);
        if (rule != null && !tryAcquire(rule, request, response)) {
            return;
        }
        if (!tryAcquire(SAFETY_NET, request, response)) {
            return;
        }
        filterChain.doFilter(request, response);
    }

    private Rule findRule(HttpServletRequest request) {
        String method = request.getMethod();
        String path = request.getRequestURI();
        if (path == null) {
            return null;
        }
        for (Rule rule : RULES) {
            if (rule.matches(method, path)) {
                return rule;
            }
        }
        return null;
    }

    private boolean tryAcquire(Rule rule, HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        String key = keyFor(rule, request);
        if (limiter.tryAcquire(key, rule.limit(), rule.window())) {
            return true;
        }
        reject(rule, key, response, limiter.retryAfterSeconds(key, rule.window()));
        return false;
    }

    private String keyFor(Rule rule, HttpServletRequest request) {
        if (rule.keyType() == KeyType.IP) {
            return ruleKey(rule, "ip", clientIp(request));
        }
        String email = currentUserEmail();
        if (email != null) {
            return ruleKey(rule, "user", email);
        }
        return ruleKey(rule, "ip", clientIp(request));
    }

    private String ruleKey(Rule rule, String type, String value) {
        return rule.name() + ":" + type + ":" + value;
    }

    private String currentUserEmail() {
        try {
            return currentUserProvider.getCurrentUserEmail();
        } catch (RuntimeException e) {
            return null;
        }
    }

    private String clientIp(HttpServletRequest request) {
        return clientIpResolver.resolve(request);
    }

    private void reject(Rule rule, String key, HttpServletResponse response, long retryAfterSeconds)
            throws IOException {
        recordRateLimited(rule, key);
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfterSeconds));
        response.setContentType(MediaType.APPLICATION_JSON_VALUE + ";charset=" + StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getWriter(), ErrorResponse.of(ErrorCode.TOO_MANY_REQUESTS));
    }

    /**
     * 같은 규칙·키의 거부는 1분에 한 번만 감사 기록해 429 폭주 시 DB 쓰기 증폭을 막는다.
     * 감사 기록이 실패해도 429 응답은 그대로 반환한다.
     */
    private void recordRateLimited(Rule rule, String key) {
        try {
            if (!auditLimiter.tryAcquire(rule.name() + ":audit:" + key, 1, ONE_MINUTE)) {
                return;
            }
            auditService.record(AuditEventType.RATE_LIMITED, AuditOutcome.DENIED,
                    currentUserEmail(), null, null, rule.name());
        } catch (RuntimeException e) {
            log.warn("요청 제한 감사 기록을 남기지 못했습니다: {}", e.getClass().getSimpleName());
        }
    }

    private enum KeyType {
        USER,
        IP
    }

    private record Rule(String name,
                        String method,
                        String pathPattern,
                        KeyType keyType,
                        int limit,
                        Duration window) {

        private boolean matches(String requestMethod, String requestPath) {
            if (method != null && !method.equalsIgnoreCase(requestMethod)) {
                return false;
            }
            if (pathPattern == null) {
                return true;
            }
            if (pathPattern.equals(API_GLOB) && !requestPath.startsWith(API_PREFIX)) {
                return false;
            }
            return PATH_MATCHER.match(pathPattern, requestPath);
        }
    }
}
