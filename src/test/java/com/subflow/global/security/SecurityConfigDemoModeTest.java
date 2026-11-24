package com.subflow.global.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * demo 모드에서 SecurityFilterChain이 실제로 적용되는지 확인한다.
 * DemoAuthenticationFilter가 없으면 anyRequest().authenticated()에 의해 /settings 는 /login 으로 리다이렉트된다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityConfigDemoModeTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("demo 모드에서는 로그인 없이 보호 페이지에 접근할 수 있다")
    void allowsProtectedPageWithDemoAuthentication() throws Exception {
        mockMvc.perform(get("/settings"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("데이터 내려받기")));
    }

    @Test
    @DisplayName("demo 모드에서는 로그인 없이 보호 API에 접근할 수 있다")
    void allowsProtectedApiWithDemoAuthentication() throws Exception {
        mockMvc.perform(get("/api/services"))
                .andExpect(status().isOk());
    }
}
