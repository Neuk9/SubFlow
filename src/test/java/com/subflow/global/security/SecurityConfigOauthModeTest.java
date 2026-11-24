package com.subflow.global.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

/**
 * oauth 모드에서 SecurityFilterChain이 실제로 적용되는지 확인한다.
 * 데모 필터가 등록되지 않아 비인증 요청이 401로 끊기고, 공개 로그인 페이지만 열린다.
 */
@SpringBootTest(properties = {
        "subflow.auth.mode=oauth",
        "subflow.security.allowed-origins[0]=https://subflow.stda.app",
        "subflow.security.audit-hash-pepper=test-pepper",
        "spring.security.oauth2.client.registration.google.client-id=test-client-id",
        "spring.security.oauth2.client.registration.google.client-secret=test-client-secret",
        "spring.security.oauth2.client.registration.google.scope=email,profile"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityConfigOauthModeTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("oauth 모드에서 비인증 API 요청은 401로 거부된다")
    void rejectsUnauthenticatedApiRequest() throws Exception {
        mockMvc.perform(get("/api/services"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("oauth 모드에서도 로그인 페이지는 공개된다")
    void allowsLoginPage() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(view().name("login"));
    }
}
