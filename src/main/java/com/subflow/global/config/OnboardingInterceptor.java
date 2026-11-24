package com.subflow.global.config;

import com.subflow.global.auth.CurrentUserProvider;
import com.subflow.global.security.AuthMode;
import com.subflow.global.security.SecurityPaths;
import com.subflow.user.domain.User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.util.Optional;

/**
 * Google 로그인 사용자가 온보딩을 마치지 않았으면 /onboarding 으로 보낸다.
 * oauth 모드에서 사용자 행이 없으면 온보딩 대신 인증 실패로 처리한다.
 */
public class OnboardingInterceptor implements HandlerInterceptor {

    private static final String ONBOARDING_PATH = "/onboarding";

    private final AuthMode mode;
    private final CurrentUserProvider currentUserProvider;

    public OnboardingInterceptor(AuthMode mode, CurrentUserProvider currentUserProvider) {
        this.mode = mode;
        this.currentUserProvider = currentUserProvider;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {
        if (mode != AuthMode.OAUTH) {
            return true;
        }
        String path = request.getRequestURI();
        if (path.equals(ONBOARDING_PATH) || SecurityPaths.isPublic(path)) {
            return true;
        }
        Optional<User> user = currentUserProvider.findCurrentUser();
        if (user.isEmpty()) {
            return rejectUnauthenticated(path, response);
        }
        if (user.get().isOnboardingCompleted()) {
            return true;
        }
        if (path.startsWith("/api/")) {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"status\":403,\"code\":\"ONBOARDING_REQUIRED\",\"message\":\"온보딩을 먼저 완료해 주세요.\"}");
            return false;
        }
        response.sendRedirect(ONBOARDING_PATH);
        return false;
    }

    private boolean rejectUnauthenticated(String path, HttpServletResponse response) throws IOException {
        if (path.startsWith("/api/")) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"status\":401,\"code\":\"UNAUTHORIZED\",\"message\":\"인증이 필요합니다.\"}");
            return false;
        }
        response.sendRedirect("/login");
        return false;
    }
}
