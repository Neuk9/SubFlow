package com.subflow.global.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * oauth 모드에서 OAuth2 리다이렉트·콜백 필터보다 앞에서 IP 요청 제한과 no-store가 동작하는지 검증한다.
 */
@SpringBootTest(properties = {
        "subflow.auth.mode=oauth",
        "subflow.security.allowed-origins[0]=https://subflow.stda.app",
        "subflow.security.audit-hash-pepper=test-pepper",
        "subflow.security.rate-limit.enabled=true",
        "spring.security.oauth2.client.registration.google.client-id=test-client-id",
        "spring.security.oauth2.client.registration.google.client-secret=test-client-secret",
        "spring.security.oauth2.client.registration.google.scope=email,profile"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OAuthRateLimitIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("GET /oauth2/authorization/google 은 no-store를 설정하고 분당 20회를 넘으면 429로 차단한다")
    void throttlesGoogleOAuthAuthorizationRedirect() throws Exception {
        for (int i = 0; i < 20; i++) {
            mockMvc.perform(get("/oauth2/authorization/google").with(req -> {
                        req.setRemoteAddr("198.51.100.10");
                        return req;
                    }))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(header().string("Cache-Control", containsString("no-store")));
        }

        mockMvc.perform(get("/oauth2/authorization/google").with(req -> {
                    req.setRemoteAddr("198.51.100.10");
                    return req;
                }))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(header().string("Cache-Control", containsString("no-store")))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("TOO_MANY_REQUESTS"));
    }

    @Test
    @DisplayName("GET /login/oauth2/code/google 콜백 실패 폭주도 분당 30회를 넘으면 429로 차단한다")
    void throttlesOAuthCallbackFlood() throws Exception {
        for (int i = 0; i < 30; i++) {
            mockMvc.perform(get("/login/oauth2/code/google").param("error", "access_denied").with(req -> {
                        req.setRemoteAddr("198.51.100.11");
                        return req;
                    }))
                    .andExpect(status().is3xxRedirection())
                    .andExpect(header().string("Cache-Control", containsString("no-store")));
        }

        mockMvc.perform(get("/login/oauth2/code/google").param("error", "access_denied").with(req -> {
                    req.setRemoteAddr("198.51.100.11");
                    return req;
                }))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.code").value("TOO_MANY_REQUESTS"));
    }
}
