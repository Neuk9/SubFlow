package com.subflow.subscription.controller;

import com.subflow.subscription.dto.InsightResponse;
import com.subflow.subscription.service.InsightService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/insights")
@RequiredArgsConstructor
public class InsightController {

    private final InsightService insightService;

    @GetMapping
    public InsightResponse getInsights() {
        return insightService.getInsights();
    }
}
