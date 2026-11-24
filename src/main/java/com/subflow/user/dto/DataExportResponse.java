package com.subflow.user.dto;

import com.subflow.subscription.dto.MemberShareResponse;
import com.subflow.subscription.dto.SubscriptionResponse;
import com.subflow.user.domain.ConsentType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record DataExportResponse(
        LocalDateTime exportedAt,
        ProfileResponse profile,
        List<ConsentRecord> consents,
        List<SubscriptionResponse> subscriptions,
        List<BillingRecordExport> billingRecords,
        List<ShareExport> ownedShares,
        List<MemberShareResponse> sharedWithMe
) {

    public record ConsentRecord(ConsentType type, String version, boolean agreed, LocalDateTime agreedAt) {
    }

    public record BillingRecordExport(
            Long subscriptionId,
            String serviceName,
            LocalDate billingDate,
            BigDecimal amount,
            String status,
            String memo
    ) {
    }

    public record ShareExport(
            Long shareId,
            Long subscriptionId,
            String serviceName,
            String memberName,
            String memberEmail,
            BigDecimal shareAmount,
            String status,
            boolean memberPaid,
            boolean confirmed
    ) {
    }
}
