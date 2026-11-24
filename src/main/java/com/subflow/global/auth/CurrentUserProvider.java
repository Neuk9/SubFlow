package com.subflow.global.auth;

import com.subflow.global.exception.BusinessException;
import com.subflow.global.exception.ErrorCode;
import com.subflow.global.security.AuthMode;
import com.subflow.global.security.AuthProperties;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.user.OAuth2User;
import com.subflow.user.domain.User;
import com.subflow.user.repository.UserRepository;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * 현재 로그인한 사용자를 해석한다.
 * demo 모드에서만 인증이 없을 때 데모 소유자로 폴백하며, oauth 모드에서는 401로 끊는다.
 */
@Component
public class CurrentUserProvider {

    public static final String LOCAL_OWNER = "local@subflow.dev";

    private final UserRepository userRepository;
    private final AuthProperties authProperties;

    public CurrentUserProvider(UserRepository userRepository, AuthProperties authProperties) {
        this.userRepository = userRepository;
        this.authProperties = authProperties;
    }

    public Optional<User> findCurrentUser() {
        return userRepository.findByEmail(getCurrentUserEmail());
    }

    public User requireCurrentUser() {
        return findCurrentUser().orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
    }

    public String getCurrentUserEmail() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null
                || authentication instanceof AnonymousAuthenticationToken
                || !authentication.isAuthenticated()) {
            return unauthenticatedEmail();
        }

        Object principal = authentication.getPrincipal();
        if (principal instanceof OAuth2User oAuth2User) {
            String email = oAuth2User.getAttribute("email");
            if (email == null || email.isBlank()) {
                throw new BusinessException(ErrorCode.UNAUTHORIZED);
            }
            return email;
        }

        return authentication.getName();
    }

    private String unauthenticatedEmail() {
        if (authProperties.mode() == AuthMode.DEMO) {
            return LOCAL_OWNER;
        }
        throw new BusinessException(ErrorCode.UNAUTHORIZED);
    }
}
