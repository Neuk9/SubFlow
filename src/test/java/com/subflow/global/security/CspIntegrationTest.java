package com.subflow.global.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;

/**
 * 정적 자원 자체 호스팅 이후 CSP가 외부 CDN 없이 {@code 'self'}만 허용하는지 확인한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class CspIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("CSP script-src는 self만 허용하고 CDN을 허용하지 않는다")
    void cspHasNoThirdPartyScriptSource() throws Exception {
        mockMvc.perform(get("/login").secure(true))
                .andExpect(header().string("Content-Security-Policy",
                        allOf(containsString("script-src 'self';"),
                                not(containsString("cdn.jsdelivr.net")))));
    }

    @Test
    @DisplayName("CSP는 외부 출처 없이 self 기반으로 유지된다")
    void cspHasNoExternalOrigins() throws Exception {
        mockMvc.perform(get("/login").secure(true))
                .andExpect(header().string("Content-Security-Policy",
                        allOf(containsString("style-src 'self' 'unsafe-inline';"),
                                containsString("font-src 'self';"),
                                not(containsString("https://")))));
    }
}
