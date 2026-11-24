package com.subflow.user.controller;

import com.subflow.global.audit.AuditEventType;
import com.subflow.global.audit.AuditOutcome;
import com.subflow.global.audit.AuditService;
import com.subflow.global.auth.CurrentUserProvider;
import com.subflow.user.dto.AccountDeleteRequest;
import com.subflow.user.dto.ConsentUpdateRequest;
import com.subflow.user.dto.DataExportResponse;
import com.subflow.user.dto.OnboardingRequest;
import com.subflow.user.dto.ProfileResponse;
import com.subflow.user.dto.ProfileUpdateRequest;
import com.subflow.user.service.AccountDeletionService;
import com.subflow.user.service.DataExportService;
import com.subflow.user.service.UserService;
import com.subflow.global.exception.BusinessException;
import com.subflow.global.exception.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/me")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final AccountDeletionService accountDeletionService;
    private final DataExportService dataExportService;
    private final CurrentUserProvider currentUserProvider;
    private final AuditService auditService;

    @GetMapping
    public ProfileResponse getProfile() {
        return userService.getProfile(currentUserProvider.getCurrentUserEmail());
    }

    @PostMapping("/onboarding")
    public ProfileResponse completeOnboarding(@Valid @RequestBody OnboardingRequest request) {
        return userService.completeOnboarding(currentUserProvider.getCurrentUserEmail(), request);
    }

    @PutMapping
    public ProfileResponse updateProfile(@Valid @RequestBody ProfileUpdateRequest request) {
        return userService.updateProfile(currentUserProvider.getCurrentUserEmail(), request);
    }

    @PutMapping("/consents")
    public ProfileResponse updateConsent(@Valid @RequestBody ConsentUpdateRequest request) {
        return userService.updateConsent(currentUserProvider.getCurrentUserEmail(), request);
    }

    @GetMapping("/data")
    public ResponseEntity<DataExportResponse> exportData() {
        String email = currentUserProvider.getCurrentUserEmail();
        DataExportResponse data = dataExportService.export(email);
        auditService.record(AuditEventType.DATA_EXPORTED, AuditOutcome.SUCCESS,
                email, "user", null, "json");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"subflow-data.json\"")
                .body(data);
    }

    @DeleteMapping
    public ResponseEntity<Void> deleteAccount(@RequestBody(required = false) AccountDeleteRequest request,
                                              HttpServletRequest httpRequest) {
        if (request == null || !"탈퇴".equals(request.confirmation())) {
            throw new BusinessException(ErrorCode.INVALID_CONFIRMATION);
        }
        accountDeletionService.deleteAccount(currentUserProvider.getCurrentUserEmail());

        if (httpRequest.getSession(false) != null) {
            httpRequest.getSession(false).invalidate();
        }
        SecurityContextHolder.clearContext();
        return ResponseEntity.noContent().build();
    }
}
