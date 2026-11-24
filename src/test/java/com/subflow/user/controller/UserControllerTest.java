package com.subflow.user.controller;

import com.subflow.global.auth.CurrentUserProvider;
import com.subflow.global.exception.GlobalExceptionHandler;
import com.subflow.subscription.domain.Category;
import com.subflow.user.domain.Membership;
import com.subflow.user.domain.Occupation;
import com.subflow.user.dto.OnboardingRequest;
import com.subflow.user.dto.ProfileResponse;
import com.subflow.user.service.AccountDeletionService;
import com.subflow.user.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class UserControllerTest {

    private static final String EMAIL = "a@b.com";

    private MockMvc mockMvc;

    @Mock
    private UserService userService;

    @Mock
    private AccountDeletionService accountDeletionService;

    @Mock
    private CurrentUserProvider currentUserProvider;

    @InjectMocks
    private UserController userController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(userController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
        given(currentUserProvider.getCurrentUserEmail()).willReturn(EMAIL);
    }

    @Test
    @DisplayName("내 프로필을 반환한다")
    void returnsProfile() throws Exception {
        given(userService.getProfile(EMAIL)).willReturn(profile(false));

        mockMvc.perform(get("/api/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(EMAIL))
                .andExpect(jsonPath("$.occupation").value("UNIVERSITY"))
                .andExpect(jsonPath("$.interests[0]").value("OTT"));
    }

    @Test
    @DisplayName("온보딩 제출을 서비스로 전달한다")
    void submitsOnboarding() throws Exception {
        given(userService.completeOnboarding(eq(EMAIL), any(OnboardingRequest.class))).willReturn(profile(true));

        mockMvc.perform(post("/api/me/onboarding")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "birthYear": 2002,
                                  "occupation": "UNIVERSITY",
                                  "householdSize": 3,
                                  "interests": ["OTT"],
                                  "memberships": ["SKT"],
                                  "agreePrivacy": true,
                                  "agreeTerms": true,
                                  "agreeMarketing": false
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.onboardingCompleted").value(true));
    }

    @Test
    @DisplayName("출생연도가 없으면 400을 반환한다")
    void rejectsMissingBirthYear() throws Exception {
        mockMvc.perform(post("/api/me/onboarding")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"occupation\":\"UNIVERSITY\",\"agreePrivacy\":true,\"agreeTerms\":true}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT_VALUE"));

        then(userService).should(org.mockito.Mockito.never()).completeOnboarding(any(), any());
    }

    @Test
    @DisplayName("프로필 수정 요청을 서비스로 전달한다")
    void updatesProfile() throws Exception {
        given(userService.updateProfile(eq(EMAIL), any())).willReturn(profile(true));

        mockMvc.perform(put("/api/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"birthYear\":2002,\"occupation\":\"UNIVERSITY\",\"householdSize\":2}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.householdSize").value(2));
    }

    @Test
    @DisplayName("탈퇴 확인 문구가 다르면 400을 반환한다")
    void rejectsWrongConfirmation() throws Exception {
        mockMvc.perform(delete("/api/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confirmation\":\"아니오\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_CONFIRMATION"));

        then(accountDeletionService).should(org.mockito.Mockito.never()).deleteAccount(any());
    }

    @Test
    @DisplayName("탈퇴 확인 문구가 맞으면 계정을 삭제하고 204를 반환한다")
    void deletesAccount() throws Exception {
        mockMvc.perform(delete("/api/me")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"confirmation\":\"탈퇴\"}"))
                .andExpect(status().isNoContent());

        then(accountDeletionService).should().deleteAccount(EMAIL);
    }

    private ProfileResponse profile(boolean completed) {
        return new ProfileResponse(1L, EMAIL, "늑구", "pic",
                2002, Occupation.UNIVERSITY, 2,
                Set.of(Category.OTT), Set.of(Membership.SKT), completed, false);
    }
}
