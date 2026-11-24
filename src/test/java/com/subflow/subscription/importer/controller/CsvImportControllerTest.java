package com.subflow.subscription.importer.controller;

import com.subflow.global.exception.BusinessException;
import com.subflow.global.exception.ErrorCode;
import com.subflow.global.exception.GlobalExceptionHandler;
import com.subflow.subscription.importer.dto.ImportConfirmResponse;
import com.subflow.subscription.importer.dto.ImportPreviewResponse;
import com.subflow.subscription.importer.dto.ImportPreviewRow;
import com.subflow.subscription.importer.matcher.MatchType;
import com.subflow.subscription.importer.parser.ColumnMapping;
import com.subflow.subscription.importer.service.CsvImportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class CsvImportControllerTest {

    private MockMvc mockMvc;

    @Mock
    private CsvImportService csvImportService;

    @InjectMocks
    private CsvImportController csvImportController;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(csvImportController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    @DisplayName("multipart 파일을 받아 미리보기를 반환한다")
    void previewsCsv() throws Exception {
        given(csvImportService.preview(any(), isNull(), isNull(), isNull(), eq(true)))
                .willReturn(previewResponse());

        mockMvc.perform(multipart("/api/imports/preview")
                        .file(new MockMultipartFile("file", "sample.csv", "text/csv", csvBytes())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rowCount").value(1))
                .andExpect(jsonPath("$.rows[0].merchant").value("넷플릭스"))
                .andExpect(jsonPath("$.rows[0].matchType").value("AUTO"));
    }

    @Test
    @DisplayName("컬럼 매핑 인덱스를 전달하면 서비스로 넘긴다")
    void passesColumnMapping() throws Exception {
        given(csvImportService.preview(any(), eq(0), eq(1), eq(2), eq(true)))
                .willReturn(previewResponse());

        mockMvc.perform(multipart("/api/imports/preview")
                        .file(new MockMultipartFile("file", "sample.csv", "text/csv", csvBytes()))
                        .param("dateColumn", "0")
                        .param("merchantColumn", "1")
                        .param("amountColumn", "2"))
                .andExpect(status().isOk());

        then(csvImportService).should().preview(any(), eq(0), eq(1), eq(2), eq(true));
    }

    @Test
    @DisplayName("빈 파일이면 400을 반환한다")
    void rejectsEmptyFile() throws Exception {
        given(csvImportService.preview(any(), any(), any(), any(), anyBoolean()))
                .willThrow(new BusinessException(ErrorCode.EMPTY_IMPORT_FILE));

        mockMvc.perform(multipart("/api/imports/preview")
                        .file(new MockMultipartFile("file", "empty.csv", "text/csv", new byte[0])))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("가져올 내역이 없는 파일입니다."));
    }

    @Test
    @DisplayName("확정 요청을 처리한다")
    void confirmsImport() throws Exception {
        given(csvImportService.confirm(any())).willReturn(new ImportConfirmResponse(3, 1, 1));

        String json = """
                {
                  "records": [
                    { "subscriptionId": 1, "billingDate": "2026-09-25", "amount": 17000, "merchant": "넷플릭스" }
                  ],
                  "newSubscriptions": []
                }
                """;

        mockMvc.perform(post("/api/imports/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.recordedCount").value(3))
                .andExpect(jsonPath("$.skippedCount").value(1))
                .andExpect(jsonPath("$.createdSubscriptions").value(1));
    }

    @Test
    @DisplayName("신규 구독 필수 값이 없으면 400을 반환한다")
    void rejectsInvalidNewSubscription() throws Exception {
        String json = """
                {
                  "records": [],
                  "newSubscriptions": [
                    { "serviceName": null, "category": "MUSIC", "fee": 10900,
                      "billingCycle": "MONTHLY", "billingDay": 15 }
                  ]
                }
                """;

        mockMvc.perform(post("/api/imports/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_INPUT_VALUE"));
    }

    private ImportPreviewResponse previewResponse() {
        ImportPreviewRow row = new ImportPreviewRow(2, LocalDate.of(2026, 9, 25), "넷플릭스",
                new BigDecimal("17000"), 1L, "넷플릭스 프리미엄", 1.0, MatchType.AUTO, false);
        return new ImportPreviewResponse("UTF-8", ',', 0, false,
                new ColumnMapping(0, 1, 2), List.of(), List.of(), 1,
                List.of(row), List.of(), 0, List.of());
    }

    private byte[] csvBytes() {
        return "거래일,이용처,승인금액\n2026-09-25,넷플릭스,17000\n".getBytes(StandardCharsets.UTF_8);
    }
}
