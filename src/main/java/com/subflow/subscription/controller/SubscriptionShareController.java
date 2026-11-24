package com.subflow.subscription.controller;

import com.subflow.subscription.dto.MemberShareResponse;
import com.subflow.subscription.dto.ShareCreateRequest;
import com.subflow.subscription.dto.SharePaymentRequest;
import com.subflow.subscription.dto.ShareResponse;
import com.subflow.subscription.dto.ShareSettleRequest;
import com.subflow.subscription.dto.ShareSummaryResponse;
import com.subflow.subscription.service.SubscriptionShareService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Validated
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class SubscriptionShareController {

    private static final String PERIOD_PATTERN = "\\d{4}-(0[1-9]|1[0-2])";
    private static final String PERIOD_MESSAGE = "조회 월은 yyyy-MM 형식이어야 합니다.";

    private final SubscriptionShareService shareService;

    @GetMapping("/subscriptions/{subscriptionId}/shares")
    public List<ShareResponse> getShares(@PathVariable Long subscriptionId,
                                         @RequestParam(required = false)
                                         @Pattern(regexp = PERIOD_PATTERN, message = PERIOD_MESSAGE)
                                         String period) {
        return shareService.getShares(subscriptionId, period);
    }

    @PostMapping("/subscriptions/{subscriptionId}/shares")
    public ShareResponse addShare(@PathVariable Long subscriptionId,
                                  @Valid @RequestBody ShareCreateRequest request) {
        return shareService.addShare(subscriptionId, request);
    }

    @DeleteMapping("/shares/{shareId}")
    public ResponseEntity<Void> removeShare(@PathVariable Long shareId) {
        shareService.removeShare(shareId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/shares/{shareId}/settlement")
    public ResponseEntity<Void> settle(@PathVariable Long shareId,
                                       @Valid @RequestBody ShareSettleRequest request) {
        shareService.settle(shareId, request);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/shares/{shareId}/accept")
    public ResponseEntity<Void> accept(@PathVariable Long shareId) {
        shareService.acceptInvitation(shareId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/shares/{shareId}/decline")
    public ResponseEntity<Void> decline(@PathVariable Long shareId) {
        shareService.declineInvitation(shareId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/shares/{shareId}/reinvite")
    public ResponseEntity<Void> reinvite(@PathVariable Long shareId) {
        shareService.reinvite(shareId);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/shares/{shareId}/payment")
    public ResponseEntity<Void> markPayment(@PathVariable Long shareId,
                                            @Valid @RequestBody SharePaymentRequest request) {
        shareService.markPayment(shareId, request);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/shares/shared")
    public List<MemberShareResponse> getSharedWithMe(@RequestParam(required = false)
                                                     @Pattern(regexp = PERIOD_PATTERN, message = PERIOD_MESSAGE)
                                                     String period) {
        return shareService.getSharedWithMe(period);
    }

    @GetMapping("/shares/summary")
    public ShareSummaryResponse getSummary(@RequestParam(required = false)
                                           @Pattern(regexp = PERIOD_PATTERN, message = PERIOD_MESSAGE)
                                           String period) {
        return shareService.getSummary(period);
    }
}
