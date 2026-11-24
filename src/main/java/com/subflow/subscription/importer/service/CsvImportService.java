package com.subflow.subscription.importer.service;

import com.subflow.global.exception.BusinessException;
import com.subflow.global.exception.ErrorCode;
import com.subflow.subscription.domain.BillingRecord;
import com.subflow.subscription.domain.BillingStatus;
import com.subflow.subscription.domain.Subscription;
import com.subflow.subscription.dto.SubscriptionCreateRequest;
import com.subflow.subscription.importer.dto.ImportConfirmRequest;
import com.subflow.subscription.importer.dto.ImportConfirmResponse;
import com.subflow.subscription.importer.dto.ImportPreviewResponse;
import com.subflow.subscription.importer.dto.ImportPreviewRow;
import com.subflow.subscription.importer.dto.ImportRecordRequest;
import com.subflow.subscription.importer.dto.SkippedRow;
import com.subflow.subscription.importer.matcher.CandidateFinder;
import com.subflow.subscription.importer.matcher.ImportCandidate;
import com.subflow.subscription.importer.matcher.MatchResult;
import com.subflow.subscription.importer.matcher.MatchType;
import com.subflow.subscription.importer.matcher.SubscriptionMatcher;
import com.subflow.subscription.importer.parser.ColumnDetection;
import com.subflow.subscription.importer.parser.ColumnDetector;
import com.subflow.subscription.importer.parser.ColumnMapping;
import com.subflow.subscription.importer.parser.CsvDocument;
import com.subflow.subscription.importer.parser.CsvParser;
import com.subflow.subscription.importer.parser.CsvRow;
import com.subflow.subscription.importer.parser.ParsedPayment;
import com.subflow.subscription.importer.parser.ValueParser;
import com.subflow.subscription.repository.BillingRecordRepository;
import com.subflow.subscription.repository.SubscriptionRepository;
import com.subflow.subscription.service.SubscriptionCommandService;
import com.subflow.subscription.service.SubscriptionFinder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CsvImportService {

    private static final int MAX_ROWS = 5_000;
    private static final int SAMPLE_ROW_COUNT = 5;
    private static final int MAX_SKIPPED_RECORDS = 200;

    private final SubscriptionRepository subscriptionRepository;
    private final BillingRecordRepository billingRecordRepository;
    private final SubscriptionFinder subscriptionFinder;
    private final SubscriptionCommandService subscriptionCommandService;

    private final CsvParser csvParser = new CsvParser();
    private final ColumnDetector columnDetector = new ColumnDetector();
    private final SubscriptionMatcher subscriptionMatcher = new SubscriptionMatcher();
    private final CandidateFinder candidateFinder = new CandidateFinder();

    public ImportPreviewResponse preview(MultipartFile file, Integer dateColumn, Integer merchantColumn,
                                         Integer amountColumn, boolean hasHeader) {
        CsvDocument document = parse(file);
        ColumnDetection detection = resolveDetection(document, dateColumn, merchantColumn, amountColumn, hasHeader);

        if (detection.needsMapping()) {
            return new ImportPreviewResponse(
                    document.encoding(), document.delimiter(), -1, true, null,
                    headers(document), sampleRows(document), 0,
                    List.of(), List.of(), 0, List.of());
        }

        int headerOffset = detection.headerRowIndex() >= 0 ? 1 : 0;
        if (document.rows().size() - headerOffset > MAX_ROWS) {
            throw new BusinessException(ErrorCode.IMPORT_LIMIT_EXCEEDED);
        }

        List<SkippedRow> skipped = new ArrayList<>();
        List<ParsedPayment> payments = toPayments(document, detection, skipped);

        MatchContext context = loadContext();
        List<ImportPreviewRow> rows = new ArrayList<>();
        List<ParsedPayment> unmatched = new ArrayList<>();

        for (ParsedPayment payment : payments) {
            Optional<MatchResult> matched = subscriptionMatcher.match(
                    payment.billingDate(), payment.merchant(), payment.amount(), context.subscriptions());

            if (matched.isEmpty()) {
                rows.add(new ImportPreviewRow(payment.rowNumber(), payment.billingDate(), payment.merchant(),
                        payment.amount(), null, null, 0, MatchType.NONE, false));
                unmatched.add(payment);
                continue;
            }

            MatchResult match = matched.get();
            boolean alreadyRecorded = match.subscriptionId() != null
                    && context.recordedKeys().contains(recordKey(match.subscriptionId(),
                    payment.billingDate(), payment.amount()));
            rows.add(new ImportPreviewRow(payment.rowNumber(), payment.billingDate(), payment.merchant(),
                    payment.amount(), match.subscriptionId(), match.serviceName(),
                    match.score(), match.type(), alreadyRecorded));
        }

        List<ImportCandidate> candidates = candidateFinder.find(unmatched, context.subscriptions());
        Set<Integer> candidateRows = candidates.stream()
                .flatMap(candidate -> candidate.rowNumbers().stream())
                .collect(Collectors.toSet());
        int oneOffCount = (int) unmatched.stream()
                .filter(payment -> !candidateRows.contains(payment.rowNumber()))
                .count();

        return new ImportPreviewResponse(
                document.encoding(), document.delimiter(), detection.headerRowIndex(), false,
                detection.mapping(), List.of(), List.of(),
                payments.size(), rows, candidates, oneOffCount, skipped);
    }

    @Transactional
    public ImportConfirmResponse confirm(ImportConfirmRequest request) {
        List<ImportRecordRequest> records = request == null || request.records() == null
                ? List.of() : request.records();
        List<SubscriptionCreateRequest> newSubscriptions = request == null || request.newSubscriptions() == null
                ? List.of() : request.newSubscriptions();

        Map<Long, Subscription> ownedSubscriptions = subscriptionFinder.currentOwnerEmail() == null
                ? Map.of()
                : subscriptionRepository
                        .findAllByOwnerEmailOrderByNextBillingDateAsc(subscriptionFinder.currentOwnerEmail())
                        .stream()
                        .collect(Collectors.toMap(Subscription::getId, Function.identity()));

        int recordedCount = 0;
        int skippedCount = 0;
        for (ImportRecordRequest record : records) {
            validate(record);
            Subscription subscription = ownedSubscriptions.get(record.subscriptionId());
            if (subscription == null) {
                throw new BusinessException(ErrorCode.SUBSCRIPTION_NOT_FOUND);
            }
            if (billingRecordRepository.existsBySubscriptionIdAndBillingDateAndAmount(
                    record.subscriptionId(), record.billingDate(), record.amount())) {
                skippedCount++;
                continue;
            }
            billingRecordRepository.save(BillingRecord.builder()
                    .subscription(subscription)
                    .billingDate(record.billingDate())
                    .amount(record.amount())
                    .status(BillingStatus.PAID)
                    .memo(importMemo(record.merchant()))
                    .build());
            recordedCount++;
        }

        for (SubscriptionCreateRequest createRequest : newSubscriptions) {
            subscriptionCommandService.createSubscription(createRequest);
        }

        return new ImportConfirmResponse(recordedCount, skippedCount, newSubscriptions.size());
    }

    private void validate(ImportRecordRequest record) {
        if (record == null || record.subscriptionId() == null || record.billingDate() == null
                || record.amount() == null || record.amount().signum() <= 0) {
            throw new BusinessException(ErrorCode.INVALID_IMPORT_ROW);
        }
    }

    private String importMemo(String merchant) {
        String memo = "가져오기: " + (merchant == null ? "" : merchant);
        return memo.length() > 255 ? memo.substring(0, 255) : memo;
    }

    private CsvDocument parse(MultipartFile file) {
        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.UNSUPPORTED_CSV);
        }
        if (content.length == 0) {
            throw new BusinessException(ErrorCode.EMPTY_IMPORT_FILE);
        }
        CsvDocument document;
        try {
            document = csvParser.parse(content);
        } catch (UncheckedIOException | IllegalStateException e) {
            throw new BusinessException(ErrorCode.UNSUPPORTED_CSV);
        }
        if (document.rows().isEmpty()) {
            throw new BusinessException(ErrorCode.EMPTY_IMPORT_FILE);
        }
        return document;
    }

    private ColumnDetection resolveDetection(CsvDocument document, Integer dateColumn, Integer merchantColumn,
                                             Integer amountColumn, boolean hasHeader) {
        ColumnDetection detected = columnDetector.detect(document, hasHeader);
        if (dateColumn == null || merchantColumn == null || amountColumn == null) {
            return detected;
        }

        int width = document.rows().stream()
                .mapToInt(row -> row.values().size())
                .max()
                .orElse(0);
        boolean duplicated = dateColumn.equals(merchantColumn)
                || dateColumn.equals(amountColumn)
                || merchantColumn.equals(amountColumn);
        boolean outOfRange = dateColumn < 0 || merchantColumn < 0 || amountColumn < 0
                || dateColumn >= width || merchantColumn >= width || amountColumn >= width;
        if (duplicated || outOfRange) {
            throw new BusinessException(ErrorCode.INVALID_COLUMN_MAPPING);
        }

        int headerRowIndex = detected.headerRowIndex() >= 0
                ? detected.headerRowIndex()
                : (hasHeader ? 0 : -1);
        return new ColumnDetection(new ColumnMapping(dateColumn, merchantColumn, amountColumn), headerRowIndex, false);
    }

    private List<ParsedPayment> toPayments(CsvDocument document, ColumnDetection detection, List<SkippedRow> skipped) {
        ColumnMapping mapping = detection.mapping();
        int start = detection.headerRowIndex() >= 0 ? detection.headerRowIndex() + 1 : 0;
        Set<String> seen = new HashSet<>();
        List<ParsedPayment> payments = new ArrayList<>();

        for (int i = start; i < document.rows().size(); i++) {
            CsvRow row = document.rows().get(i);
            String rawDate = valueAt(row, mapping.date());
            String rawAmount = valueAt(row, mapping.amount());
            String merchant = valueAt(row, mapping.merchant());

            if (isBlank(rawDate) && isBlank(rawAmount) && isBlank(merchant)) {
                continue;
            }

            Optional<LocalDate> date = ValueParser.parseDate(rawDate);
            if (date.isEmpty()) {
                addSkipped(skipped, row.rowNumber(), "날짜 없음");
                continue;
            }
            Optional<BigDecimal> amount = ValueParser.parseAmount(rawAmount);
            if (amount.isEmpty() || amount.get().signum() <= 0) {
                addSkipped(skipped, row.rowNumber(), "금액 없음");
                continue;
            }
            if (isBlank(merchant)) {
                addSkipped(skipped, row.rowNumber(), "가맹점 없음");
                continue;
            }

            String trimmedMerchant = merchant.trim();
            String key = date.get() + "|" + trimmedMerchant + "|" + amount.get().stripTrailingZeros().toPlainString();
            if (!seen.add(key)) {
                addSkipped(skipped, row.rowNumber(), "중복 행");
                continue;
            }
            BigDecimal normalizedAmount = amount.get().setScale(2, RoundingMode.HALF_UP);
            payments.add(new ParsedPayment(row.rowNumber(), date.get(), trimmedMerchant, normalizedAmount));
        }
        return payments;
    }

    private void addSkipped(List<SkippedRow> skipped, int rowNumber, String reason) {
        if (skipped.size() < MAX_SKIPPED_RECORDS) {
            skipped.add(new SkippedRow(rowNumber, reason));
        }
    }

    private MatchContext loadContext() {
        String ownerEmail = subscriptionFinder.currentOwnerEmail();
        List<Subscription> subscriptions = subscriptionRepository
                .findAllByOwnerEmailOrderByNextBillingDateAsc(ownerEmail);
        Set<String> recordedKeys = billingRecordRepository.findAllBySubscriptionOwnerEmail(ownerEmail).stream()
                .map(this::recordKey)
                .collect(Collectors.toSet());
        return new MatchContext(subscriptions, recordedKeys);
    }

    private String recordKey(BillingRecord record) {
        return recordKey(record.getSubscription().getId(), record.getBillingDate(), record.getAmount());
    }

    private String recordKey(Long subscriptionId, LocalDate date, BigDecimal amount) {
        return subscriptionId + "|" + date + "|" + amount.stripTrailingZeros().toPlainString();
    }

    private List<String> headers(CsvDocument document) {
        return document.rows().stream()
                .max(Comparator.comparingInt(row -> row.values().size()))
                .map(CsvRow::values)
                .orElse(List.of());
    }

    private List<List<String>> sampleRows(CsvDocument document) {
        int widest = document.rows().stream()
                .mapToInt(row -> row.values().size())
                .max()
                .orElse(0);
        return document.rows().stream()
                .filter(row -> row.values().size() == widest)
                .limit(SAMPLE_ROW_COUNT)
                .map(CsvRow::values)
                .toList();
    }

    private String valueAt(CsvRow row, int column) {
        return column >= 0 && column < row.values().size() ? row.values().get(column) : null;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private record MatchContext(List<Subscription> subscriptions, Set<String> recordedKeys) {
    }
}
