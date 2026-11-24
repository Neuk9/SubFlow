package com.subflow.subscription.controller;

import com.subflow.subscription.dto.ShareCreateRequest;
import com.subflow.subscription.dto.ShareResponse;
import com.subflow.subscription.dto.ShareSettleRequest;
import com.subflow.subscription.dto.ShareSummaryResponse;
import com.subflow.subscription.service.SubscriptionShareService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
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

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class SubscriptionShareController {

    private final SubscriptionShareService shareService;

    @GetMapping("/subscriptions/{subscriptionId}/shares")
    public List<ShareResponse> getShares(@PathVariable Long subscriptionId,
                                         @RequestParam(required = false) String period) {
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

    @GetMapping("/shares/summary")
    public ShareSummaryResponse getSummary(@RequestParam(required = false) String period) {
        return shareService.getSummary(period);
    }
}
