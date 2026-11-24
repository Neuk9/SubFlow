package com.subflow.subscription.importer.controller;

import com.subflow.subscription.importer.dto.ImportConfirmRequest;
import com.subflow.subscription.importer.dto.ImportConfirmResponse;
import com.subflow.subscription.importer.dto.ImportPreviewResponse;
import com.subflow.subscription.importer.service.CsvImportService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Validated
@RestController
@RequestMapping("/api/imports")
@RequiredArgsConstructor
public class CsvImportController {

    private final CsvImportService csvImportService;

    @PostMapping("/preview")
    public ImportPreviewResponse preview(
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false)
            @Min(value = 0, message = "컬럼 인덱스는 0 이상이어야 합니다.")
            @Max(value = 100, message = "컬럼 인덱스는 100 이하여야 합니다.")
            Integer dateColumn,
            @RequestParam(required = false)
            @Min(value = 0, message = "컬럼 인덱스는 0 이상이어야 합니다.")
            @Max(value = 100, message = "컬럼 인덱스는 100 이하여야 합니다.")
            Integer merchantColumn,
            @RequestParam(required = false)
            @Min(value = 0, message = "컬럼 인덱스는 0 이상이어야 합니다.")
            @Max(value = 100, message = "컬럼 인덱스는 100 이하여야 합니다.")
            Integer amountColumn,
            @RequestParam(defaultValue = "true") boolean hasHeader) {
        return csvImportService.preview(file, dateColumn, merchantColumn, amountColumn, hasHeader);
    }

    @PostMapping("/confirm")
    public ImportConfirmResponse confirm(@Valid @RequestBody ImportConfirmRequest request) {
        return csvImportService.confirm(request);
    }
}
