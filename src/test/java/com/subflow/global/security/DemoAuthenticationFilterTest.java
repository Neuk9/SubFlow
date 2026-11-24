package com.subflow.global.security;

import com.subflow.global.auth.CurrentUserProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DemoAuthenticationFilterTest {

    private final DemoAuthenticationFilter filter = new DemoAuthenticationFilter();

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("인증이 없으면 데모 사용자 인증을 주입하고 다음 필터로 넘긴다")
    void injectsDemoAuthentication() throws Exception {
        SecurityContextHolder.clearContext();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(new MockHttpServletRequest("GET", "/"),
                new MockHttpServletResponse(), chain);

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication).isNotNull();
        assertThat(authentication.getName()).isEqualTo(CurrentUserProvider.LOCAL_OWNER);
        assertThat(authentication.getAuthorities())
                .extracting("authority")
                .containsExactly("ROLE_DEMO");
        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    @DisplayName("이미 인증이 있으면 기존 인증을 덮어쓰지 않는다")
    void keepsExistingAuthentication() throws Exception {
        Authentication existing = new UsernamePasswordAuthenticationToken("other@example.com", null,
                List.of(new SimpleGrantedAuthority("ROLE_USER")));
        SecurityContextHolder.getContext().setAuthentication(existing);

        filter.doFilter(new MockHttpServletRequest("GET", "/"),
                new MockHttpServletResponse(), new MockFilterChain());

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isSameAs(existing);
    }
}
