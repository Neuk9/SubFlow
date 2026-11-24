package com.subflow.global.logging;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * API 응답, OAuth2/로그아웃 흐름, 인증된 HTML 응답에 {@code Cache-Control: no-store}를 설정한다.
 * CSS·JS·이미지 같은 정적 자원은 캐시를 허용하기 위해 제외한다.
 */
public class NoStoreFilter extends OncePerRequestFilter {

    private static final List<String> STATIC_PREFIXES = List.of(
            "/css/", "/js/", "/vendor/", "/logos/", "/images/");
    private static final String FAVICON_PATH = "/favicon.ico";

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        if (shouldNotStore(request)) {
            response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        }
        filterChain.doFilter(request, response);
    }

    private boolean shouldNotStore(HttpServletRequest request) {
        String path = pathWithinApplication(request);
        if (isStaticAsset(path)) {
            return false;
        }
        return path.startsWith("/api/")
                || path.startsWith("/oauth2/")
                || path.startsWith("/login/oauth2/")
                || "/logout".equals(path)
                || isAuthenticated();
    }

    private String pathWithinApplication(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isEmpty() && uri.startsWith(contextPath)) {
            return uri.substring(contextPath.length());
        }
        return uri;
    }

    private boolean isStaticAsset(String path) {
        if (FAVICON_PATH.equals(path)) {
            return true;
        }
        return STATIC_PREFIXES.stream().anyMatch(path::startsWith);
    }

    private boolean isAuthenticated() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken);
    }
}
