package com.subflow.subscription.controller;

import com.subflow.subscription.domain.BillingCycle;
import com.subflow.subscription.domain.Category;
import com.subflow.subscription.service.SubscriptionQueryService;
import com.subflow.subscription.service.SubscriptionStatisticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
@RequiredArgsConstructor
public class SubscriptionViewController {

    private final SubscriptionQueryService subscriptionQueryService;
    private final SubscriptionStatisticsService statisticsService;

    @GetMapping("/")
    public String index(Model model) {
        model.addAttribute("categories", Category.values());
        model.addAttribute("billingCycles", BillingCycle.values());
        model.addAttribute("summary", statisticsService.getSummary());
        model.addAttribute("subscriptions", subscriptionQueryService.getSubscriptions(null));
        return "index";
    }
}
