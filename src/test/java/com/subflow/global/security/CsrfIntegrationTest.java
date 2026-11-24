package com.subflow.global.security;

import com.subflow.global.audit.AuditEventType;
import com.subflow.global.audit.AuditRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * CSRF 보호와 Origin 검증이 실제 보안 필터 체인에서 동작하는지 확인한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CsrfIntegrationTest {

    private static final String REQUEST_BODY = """
            {"serviceName":"테스트","category":"OTT","fee":1000,"billingCycle":"MONTHLY","billingDay":1}
            """;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AuditRepository auditRepository;

    @Test
    @DisplayName("CSRF 토큰 없는 POST 요청은 403으로 거부되고 CSRF_REJECTED 감사를 남긴다")
    void rejectsPostWithoutCsrfToken() throws Exception {
        long before = countCsrfRejections();

        mockMvc.perform(post("/api/subscriptions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REQUEST_BODY))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));

        assertThat(countCsrfRejections()).isEqualTo(before + 1);
    }

    @Test
    @DisplayName("CSRF 토큰이 있는 POST 요청은 정상 처리된다")
    void acceptsPostWithCsrfToken() throws Exception {
        mockMvc.perform(post("/api/subscriptions")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REQUEST_BODY))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("허용되지 않은 Origin의 POST 요청은 403으로 거부된다")
    void rejectsDisallowedOrigin() throws Exception {
        mockMvc.perform(post("/api/subscriptions")
                        .with(csrf())
                        .header("Origin", "https://evil.example")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(REQUEST_BODY))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ORIGIN_NOT_ALLOWED"));
    }

    private long countCsrfRejections() {
        return auditRepository.findAll().stream()
                .filter(event -> event.getEventType() == AuditEventType.CSRF_REJECTED)
                .count();
    }

    @Test
    @DisplayName("설정 페이지는 실제 CSRF 토큰이 채워진 meta 태그를 렌더링한다")
    void rendersCsrfTokenMetaOnSettingsPage() throws Exception {
        mockMvc.perform(get("/settings"))
                .andExpect(status().isOk())
                .andExpect(content().string(matchesPattern(
                        "(?s).*<meta name=\"_csrf\" content=\"[^\"]+\">.*")))
                .andExpect(content().string(matchesPattern(
                        "(?s).*<meta name=\"_csrf_header\" content=\"X-CSRF-TOKEN\">.*")));
    }
}
