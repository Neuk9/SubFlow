package com.subflow.subscription.controller;

import com.subflow.global.exception.GlobalExceptionHandler;
import com.subflow.subscription.service.SubscriptionShareService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class ShareReinviteControllerTest {

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
    @DisplayName("재초대 요청은 서비스에 위임되고 204를 반환한다")
    void reinvitesShare() throws Exception {
        mockMvc.perform(post("/api/shares/{id}/reinvite", 10L))
                .andExpect(status().isNoContent());

        then(shareService).should().reinvite(10L);
    }
}
