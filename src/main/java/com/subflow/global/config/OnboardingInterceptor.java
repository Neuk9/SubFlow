package com.subflow.global.config;

import com.subflow.global.auth.CurrentUserProvider;
import com.subflow.user.domain.User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.util.List;

/**
 * Google 로그인 사용자가 온보딩을 마치지 않았으면 /onboarding 으로 보낸다.
 */
public class OnboardingInterceptor implements HandlerInterceptor {

    private static final List<String> PUBLIC_PATHS = List.of(
            "/onboarding", "/api/me", "/privacy", "/terms", "/login", "/logout",
            "/oauth2", "/login/oauth2", "/css", "/js", "/logos", "/images", "/error");

    private final boolean authEnabled;
    private final CurrentUserProvider currentUserProvider;

    public OnboardingInterceptor(boolean authEnabled, CurrentUserProvider currentUserProvider) {
        this.authEnabled = authEnabled;
        this.currentUserProvider = currentUserProvider;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {
        if (!authEnabled) {
            return true;
        }
        String path = request.getRequestURI();
        if (PUBLIC_PATHS.stream().anyMatch(path::startsWith)) {
            return true;
        }
        boolean completed = currentUserProvider.findCurrentUser()
                .map(User::isOnboardingCompleted)
                .orElse(true);
        if (completed) {
            return true;
        }
        if (path.startsWith("/api/")) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"status\":403,\"code\":\"ONBOARDING_REQUIRED\",\"message\":\"온보딩을 먼저 완료해 주세요.\"}");
            return false;
        }
        response.sendRedirect("/onboarding");
        return false;
    }
}
