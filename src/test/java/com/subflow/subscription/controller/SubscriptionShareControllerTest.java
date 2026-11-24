package com.subflow.subscription.controller;

import com.subflow.global.exception.GlobalExceptionHandler;
import com.subflow.subscription.dto.ShareCreateRequest;
import com.subflow.subscription.dto.ShareResponse;
import com.subflow.subscription.dto.ShareSettleRequest;
import com.subflow.subscription.dto.ShareSummaryResponse;
import com.subflow.subscription.service.SubscriptionShareService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class SubscriptionShareControllerTest {

    private MockMvc mockMvc;

    @Mock
    private SubscriptionShareService shareService;

    @InjectMocks
    private SubscriptionShareController shareController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(shareController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("공유 멤버 목록을 정산 상태와 함께 반환한다")
    void returnsShares() throws Exception {
        given(shareService.getShares(eq(1L), eq("2026-11"))).willReturn(List.of(
                new ShareResponse(10L, "민수", new BigDecimal("8500"), true, LocalDate.of(2026, 11, 20))));

        mockMvc.perform(get("/api/subscriptions/{id}/shares", 1L).param("period", "2026-11"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].memberName").value("민수"))
                .andExpect(jsonPath("$[0].settled").value(true));
    }

    @Test
    @DisplayName("멤버 추가 요청을 서비스로 전달한다")
    void addsShare() throws Exception {
        given(shareService.addShare(eq(1L), any(ShareCreateRequest.class)))
                .willReturn(new ShareResponse(10L, "민수", new BigDecimal("8500"), false, null));

        mockMvc.perform(post("/api/subscriptions/{id}/shares", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"memberName\":\"민수\",\"shareAmount\":8500}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.memberName").value("민수"));
    }

    @Test
    @DisplayName("멤버 이름이 비어 있으면 400을 반환한다")
    void rejectsBlankMemberName() throws Exception {
        mockMvc.perform(post("/api/subscriptions/{id}/shares", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"memberName\":\" \",\"shareAmount\":8500}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT_VALUE"));

        then(shareService).should(never()).addShare(any(), any());
    }

    @Test
    @DisplayName("정산 완료 처리와 삭제는 204를 반환한다")
    void settlesAndRemovesShare() throws Exception {
        mockMvc.perform(put("/api/shares/{id}/settlement", 10L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"period\":\"2026-11\",\"settled\":true}"))
                .andExpect(status().isNoContent());

        mockMvc.perform(delete("/api/shares/{id}", 10L))
                .andExpect(status().isNoContent());

        then(shareService).should().settle(eq(10L), any(ShareSettleRequest.class));
        then(shareService).should().removeShare(10L);
    }

    @Test
    @DisplayName("정산 월 형식이 잘못되면 400을 반환한다")
    void rejectsInvalidPeriod() throws Exception {
        mockMvc.perform(put("/api/shares/{id}/settlement", 10L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"period\":\"2026-13\",\"settled\":true}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT_VALUE"));
    }

    @Test
    @DisplayName("정산 요약을 반환한다")
    void returnsSummary() throws Exception {
        given(shareService.getSummary("2026-11")).willReturn(new ShareSummaryResponse(
                "2026-11", 2, new BigDecimal("17000"), new BigDecimal("8500"), new BigDecimal("8500")));

        mockMvc.perform(get("/api/shares/summary").param("period", "2026-11"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.memberCount").value(2))
                .andExpect(jsonPath("$.pending").value(8500));
    }
}
