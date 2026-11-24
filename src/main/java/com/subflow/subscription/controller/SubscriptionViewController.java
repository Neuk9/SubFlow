package com.subflow.subscription.controller;

import com.subflow.subscription.domain.BillingCycle;
import com.subflow.subscription.domain.Category;
import com.subflow.subscription.dto.SubscriptionResponse;
import com.subflow.subscription.service.SubscriptionQueryService;
import com.subflow.subscription.service.SubscriptionStatisticsService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.List;

@Controller
@RequiredArgsConstructor
public class SubscriptionViewController {

    private final SubscriptionQueryService subscriptionQueryService;
    private final SubscriptionStatisticsService statisticsService;
    private final Environment environment;

    @GetMapping("/")
    public String index(Model model) {
        List<SubscriptionResponse> subscriptions = subscriptionQueryService.getSubscriptions(null);
        model.addAttribute("categories", Category.values());
        model.addAttribute("billingCycles", BillingCycle.values());
        model.addAttribute("summary", statisticsService.getSummary());
        model.addAttribute("subscriptions", subscriptions);
        model.addAttribute("subscriptionCount", subscriptions.size());
        model.addAttribute("localProfile", environment.acceptsProfiles(Profiles.of("local")));
        return "index";
    }
}
