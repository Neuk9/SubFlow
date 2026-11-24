package com.subflow.subscription.controller;

import com.subflow.global.exception.GlobalExceptionHandler;
import com.subflow.subscription.dto.InsightResponse;
import com.subflow.subscription.service.InsightService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.util.List;

import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class InsightControllerTest {

    private MockMvc mockMvc;

    @Mock
    private InsightService insightService;

    @InjectMocks
    private InsightController insightController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(insightController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("인사이트 조회는 절약액과 중복 구독을 반환한다")
    void returnsInsights() throws Exception {
        given(insightService.getInsights()).willReturn(new InsightResponse(
                List.of(new InsightResponse.AnnualSaving("노션", "노션 Plus (연간)",
                        new BigDecimal("15000"), new BigDecimal("120000"), new BigDecimal("60000"))),
                new BigDecimal("60000"),
                List.of(new InsightResponse.StudentSaving("스포티파이", "스포티파이 학생",
                        new BigDecimal("10900"), new BigDecimal("5900"), new BigDecimal("5000"))),
                new BigDecimal("5000"),
                List.of(new InsightResponse.DuplicateGroup("음악 스트리밍",
                        List.of("스포티파이 개인", "애플 뮤직 개인"), new BigDecimal("21900")))));

        mockMvc.perform(get("/api/insights"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.annualSavingTotal").value(60000))
                .andExpect(jsonPath("$.annualSavings[0].serviceName").value("노션"))
                .andExpect(jsonPath("$.studentSavingTotal").value(5000))
                .andExpect(jsonPath("$.duplicateGroups[0].group").value("음악 스트리밍"))
                .andExpect(jsonPath("$.duplicateGroups[0].monthlyTotal").value(21900));
    }
}
