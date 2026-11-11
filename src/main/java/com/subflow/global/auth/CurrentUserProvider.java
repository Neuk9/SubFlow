package com.subflow.global.auth;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Component;

/**
 * 현재 로그인한 사용자의 이메일을 반환한다.
 * 인증이 비활성화된 local 프로필에서는 기본 소유자를 사용한다.
 */
@Component
public class CurrentUserProvider {

    public static final String LOCAL_OWNER = "local@subflow.dev";

    public String getCurrentUserEmail() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return LOCAL_OWNER;
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof OAuth2User oAuth2User) {
            String email = oAuth2User.getAttribute("email");
            if (email != null && !email.isBlank()) {
                return email;
            }
        }

        return LOCAL_OWNER;
    }
}
