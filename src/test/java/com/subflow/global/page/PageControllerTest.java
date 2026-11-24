package com.subflow.global.page;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@SpringBootTest
@ActiveProfiles("test")
class PageControllerTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).build();
    }

    @Test
    @DisplayName("로그인 페이지는 Google 로그인 링크를 제공한다")
    void loginPage() throws Exception {
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(view().name("login"))
                .andExpect(content().string(containsString("/oauth2/authorization/google")));
    }

    @Test
    @DisplayName("온보딩 페이지는 필수 입력 항목을 제공한다")
    void onboardingPage() throws Exception {
        mockMvc.perform(get("/onboarding"))
                .andExpect(status().isOk())
                .andExpect(view().name("onboarding"))
                .andExpect(content().string(containsString("출생연도")))
                .andExpect(content().string(containsString("개인정보 수집·이용")));
    }

    @Test
    @DisplayName("개인정보 처리방침은 운영자 연락처를 포함한다")
    void privacyPage() throws Exception {
        mockMvc.perform(get("/privacy"))
                .andExpect(status().isOk())
                .andExpect(view().name("privacy"))
                .andExpect(content().string(containsString("jjw@jjw.kr")))
                .andExpect(content().string(containsString("늑구")));
    }

    @Test
    @DisplayName("이용약관 페이지를 제공한다")
    void termsPage() throws Exception {
        mockMvc.perform(get("/terms"))
                .andExpect(status().isOk())
                .andExpect(view().name("terms"))
                .andExpect(content().string(containsString("이용약관")));
    }
}
