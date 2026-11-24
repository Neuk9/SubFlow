package com.subflow.global.security;

import jakarta.servlet.ServletContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.web.server.Cookie;
import org.springframework.boot.web.server.autoconfigure.ServerProperties;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;

/**
 * 보안 헤더, 세션 쿠키, API 캐시 정책이 실제 필터 체인과 서블릿 컨테이너에 적용되는지 확인한다.
 *
 * <p>테스트 프로필은 demo 인증을 사용하므로, prod와 같은 엄격한 프레임 정책을 검증하기 위해
 * oauth 모드로 컨텍스트를 띄운다. 세션 쿠키는 실제 {@link ServletContext} 설정을 확인한다.</p>
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "server.servlet.session.cookie.secure=true",
                "subflow.auth.mode=oauth",
                "subflow.security.allowed-origins[0]=https://subflow.stda.app",
                "subflow.security.audit-hash-pepper=test-pepper",
                "spring.security.oauth2.client.registration.google.client-id=test-client-id",
                "spring.security.oauth2.client.registration.google.client-secret=test-client-secret",
                "spring.security.oauth2.client.registration.google.scope=email,profile"
        })
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityHeadersIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ServletContext servletContext;

    @Autowired
    private ServerProperties serverProperties;

    @Test
    @DisplayName("Secure 요청에는 보안 헤더가 모두 설정된다")
    void emitsSecurityHeadersOnSecureRequest() throws Exception {
        mockMvc.perform(get("/login").secure(true))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Referrer-Policy", "strict-origin-when-cross-origin"))
                .andExpect(header().string("Permissions-Policy",
                        "camera=(), microphone=(), geolocation=()"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("Content-Security-Policy",
                        allOf(containsString("default-src 'self'"),
                                containsString("frame-ancestors 'none'"))))
                .andExpect(header().string("Strict-Transport-Security",
                        containsString("max-age=31536000")));
    }

    @Test
    @DisplayName("평문 HTTP 요청에는 HSTS를 보내지 않는다")
    void doesNotEmitHstsOnPlainHttp() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(header().doesNotExist("Strict-Transport-Security"));
    }

    @Test
    @DisplayName("API 응답은 캐시하지 않는다")
    void apiResponsesAreNotStored() throws Exception {
        mockMvc.perform(get("/api/services"))
                .andExpect(header().string("Cache-Control", containsString("no-store")));
    }

    @Test
    @DisplayName("OAuth2 로그인 시작과 로그아웃 응답은 캐시하지 않는다")
    void oauthAndLogoutResponsesAreNotStored() throws Exception {
        mockMvc.perform(get("/oauth2/authorization/google"))
                .andExpect(header().string("Cache-Control", containsString("no-store")));
        mockMvc.perform(post("/logout").with(csrf()))
                .andExpect(header().string("Cache-Control", containsString("no-store")));
    }

    @Test
    @DisplayName("인증된 HTML 응답은 캐시하지 않는다")
    void authenticatedHtmlIsNotStored() throws Exception {
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(new UsernamePasswordAuthenticationToken(
                "test-user", "n/a", List.of(new SimpleGrantedAuthority("ROLE_USER"))));
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(HttpSessionSecurityContextRepository.SPRING_SECURITY_CONTEXT_KEY, context);

        mockMvc.perform(get("/settings").session(session))
                .andExpect(header().string("Cache-Control", containsString("no-store")));
    }

    @Test
    @DisplayName("정적 자원은 캐시를 막지 않는다")
    void staticAssetsAreCacheable() throws Exception {
        mockMvc.perform(get("/css/app.css"))
                .andExpect(header().doesNotExist("Cache-Control"));
    }

    @Test
    @DisplayName("세션 쿠키는 HttpOnly, SameSite=Lax, Secure로 설정된다")
    void sessionCookieIsHardened() {
        var config = servletContext.getSessionCookieConfig();
        assertThat(config.isHttpOnly()).isTrue();
        assertThat(config.isSecure()).isTrue();
        assertThat(serverProperties.getServlet().getSession().getCookie().getSameSite())
                .isEqualTo(Cookie.SameSite.LAX);
    }

    @Test
    @DisplayName("세션 타임아웃은 30분이고 쿠키 추적만 사용한다")
    void sessionPolicyIsBounded() {
        var session = serverProperties.getServlet().getSession();
        assertThat(session.getTimeout()).isEqualTo(Duration.ofMinutes(30));
        assertThat(session.getTrackingModes())
                .containsExactly(org.springframework.boot.web.server.servlet.Session.SessionTrackingMode.COOKIE);
    }
}
