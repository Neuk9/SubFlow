package com.subflow.global.page;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 처리방침이 Cloudflare 위탁 사실을 알리고, 제거된 CDN 위탁 문구를 남기지 않는지 확인한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PrivacyPolicyTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("처리방침은 Cloudflare 위탁 항목을 포함한다")
    void privacyPolicyDisclosesCloudflare() throws Exception {
        mockMvc.perform(get("/privacy"))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Cloudflare")))
                .andExpect(content().string(containsString("Tunnel")))
                .andExpect(content().string(containsString("TLS")))
                .andExpect(content().string(containsString("접속 로그")));
    }

    @Test
    @DisplayName("처리방침은 제거된 jsDelivr CDN 위탁 문구를 포함하지 않는다")
    void privacyPolicyDoesNotMentionJsdelivr() throws Exception {
        mockMvc.perform(get("/privacy"))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("jsDelivr"))));
    }
}
