package com.subflow.global.page;

import com.subflow.subscription.domain.Category;
import com.subflow.user.domain.Membership;
import com.subflow.user.domain.Occupation;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class PageController {

    private final ObjectProvider<ClientRegistrationRepository> clientRegistrations;
    private final boolean authEnabled;

    public PageController(ObjectProvider<ClientRegistrationRepository> clientRegistrations,
                          @Value("${subflow.auth.enabled:false}") boolean authEnabled) {
        this.clientRegistrations = clientRegistrations;
        this.authEnabled = authEnabled;
    }

    @GetMapping("/login")
    public String login() {
        return "login";
    }

    @GetMapping("/onboarding")
    public String onboarding(Model model) {
        model.addAttribute("categories", Category.values());
        model.addAttribute("occupations", Occupation.values());
        model.addAttribute("memberships", Membership.values());
        return "onboarding";
    }

    @GetMapping("/settings")
    public String settings(Model model) {
        model.addAttribute("authEnabled", authEnabled && clientRegistrations.getIfAvailable() != null);
        return "settings";
    }

    @GetMapping("/privacy")
    public String privacy() {
        return "privacy";
    }

    @GetMapping("/terms")
    public String terms() {
        return "terms";
    }
}
