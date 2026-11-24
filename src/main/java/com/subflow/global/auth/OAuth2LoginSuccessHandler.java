package com.subflow.global.auth;

import com.subflow.global.audit.AuditEventType;
import com.subflow.global.audit.AuditOutcome;
import com.subflow.global.audit.AuditService;
import com.subflow.user.domain.User;
import com.subflow.user.service.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SavedRequestAwareAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class OAuth2LoginSuccessHandler extends SavedRequestAwareAuthenticationSuccessHandler {

    private final UserService userService;
    private final AuditService auditService;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
                                        Authentication authentication) throws IOException {
        String target = "/";
        if (authentication.getPrincipal() instanceof OAuth2User oAuth2User) {
            String email = oAuth2User.getAttribute("email");
            User user = userService.findByEmail(email).orElse(null);
            boolean onboardingCompleted = user != null && user.isOnboardingCompleted();
            target = onboardingCompleted ? "/" : "/onboarding";
            auditService.record(AuditEventType.LOGIN_SUCCESS, AuditOutcome.SUCCESS,
                    email, "user", user == null ? null : user.getId(), "oauth2");
        }
        response.sendRedirect(target);
    }
}
