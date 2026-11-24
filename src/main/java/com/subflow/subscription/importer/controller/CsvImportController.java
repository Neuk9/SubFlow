package com.subflow.subscription.importer.controller;

import com.subflow.subscription.importer.dto.ImportConfirmRequest;
import com.subflow.subscription.importer.dto.ImportConfirmResponse;
import com.subflow.subscription.importer.dto.ImportPreviewResponse;
import com.subflow.subscription.importer.service.CsvImportService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/imports")
@RequiredArgsConstructor
public class CsvImportController {

    private final CsvImportService csvImportService;

    @PostMapping("/preview")
    public ImportPreviewResponse preview(
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) Integer dateColumn,
            @RequestParam(required = false) Integer merchantColumn,
            @RequestParam(required = false) Integer amountColumn,
            @RequestParam(defaultValue = "true") boolean hasHeader) {
        return csvImportService.preview(file, dateColumn, merchantColumn, amountColumn, hasHeader);
    }

    @PostMapping("/confirm")
    public ImportConfirmResponse confirm(@Valid @RequestBody ImportConfirmRequest request) {
        return csvImportService.confirm(request);
    }
}
