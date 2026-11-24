package com.subflow.global.page;

import com.subflow.subscription.domain.Category;
import com.subflow.user.domain.Membership;
import com.subflow.user.domain.Occupation;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class PageController {

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
    public String settings() {
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
