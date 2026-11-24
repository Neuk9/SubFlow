package com.subflow.global.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.subflow.global.auth.CurrentUserProvider;
import com.subflow.user.domain.User;
import com.subflow.user.repository.UserRepository;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 프로퍼티로 요청 제한을 켰을 때 실제 보안 필터 체인이 429와 Retry-After를 반환하는지 확인한다.
 */
@SpringBootTest(properties = "subflow.security.rate-limit.enabled=true")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RateLimitIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void setUpDemoUser() {
        if (userRepository.findByEmail(CurrentUserProvider.LOCAL_OWNER).isEmpty()) {
            userRepository.save(User.builder()
                    .googleSub("local-rate-limit")
                    .email(CurrentUserProvider.LOCAL_OWNER)
                    .name("늑구")
                    .build());
        }
    }

    @Test
    @DisplayName("GET /api/me/data 는 분당 3회를 넘으면 429와 Retry-After를 반환한다")
    void rejectsFourthDataExportRequest() throws Exception {
        mockMvc.perform(get("/api/me/data")).andExpect(status().isOk());
        mockMvc.perform(get("/api/me/data")).andExpect(status().isOk());
        mockMvc.perform(get("/api/me/data")).andExpect(status().isOk());

        mockMvc.perform(get("/api/me/data"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("TOO_MANY_REQUESTS"));
    }
}
