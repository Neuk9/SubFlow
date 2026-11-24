package com.subflow.subscription.importer.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ImportConfirmValidationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("records가 null이면 400을 반환한다")
    void rejectsNullRecords() throws Exception {
        mockMvc.perform(post("/api/imports/confirm").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"records\":null,\"newSubscriptions\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT_VALUE"));
    }

    @Test
    @DisplayName("records가 5,000건을 넘으면 400을 반환한다")
    void rejectsTooManyRecords() throws Exception {
        String records = IntStream.range(0, 5001)
                .mapToObj(i -> "{\"subscriptionId\":1,\"billingDate\":\"2026-11-01\",\"amount\":1000,\"merchant\":\"m\"}")
                .collect(Collectors.joining(","));
        mockMvc.perform(post("/api/imports/confirm").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"records\":[" + records + "],\"newSubscriptions\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT_VALUE"));
    }

    @Test
    @DisplayName("레코드 금액이 음수이면 400을 반환한다")
    void rejectsNegativeImportAmount() throws Exception {
        mockMvc.perform(post("/api/imports/confirm").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"records\":[{\"subscriptionId\":1,\"billingDate\":\"2026-11-01\",\"amount\":-1,\"merchant\":\"m\"}],\"newSubscriptions\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT_VALUE"));
    }

    @Test
    @DisplayName("레코드 필수 값이 없으면 400을 반환한다")
    void rejectsMissingRecordField() throws Exception {
        mockMvc.perform(post("/api/imports/confirm").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"records\":[{\"subscriptionId\":1,\"merchant\":\"m\"}],\"newSubscriptions\":[]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT_VALUE"));
    }

    @Test
    @DisplayName("1MB를 넘는 JSON 본문은 413으로 거부한다")
    void rejectsOversizedJsonBody() throws Exception {
        String body = "{\"records\":[],\"newSubscriptions\":[],\"padding\":\""
                + "x".repeat(1024 * 1024) + "\"}";
        mockMvc.perform(post("/api/imports/confirm").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.code").value("REQUEST_TOO_LARGE"));
    }
}
