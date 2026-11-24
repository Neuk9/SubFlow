package com.subflow.global.security;

import com.subflow.global.page.PageController;
import com.subflow.subscription.controller.SubscriptionViewController;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.util.Set;

/**
 * CSRF 보호가 필요한 페이지에만 토큰을 모델에 노출한다.
 * Mustache 템플릿은 {{_csrf.token}}, {{_csrf.headerName}}으로 meta 태그를 만든다.
 */
@ControllerAdvice(assignableTypes = {PageController.class, SubscriptionViewController.class})
public class CsrfTokenModelAdvice {

    private static final Set<String> CSRF_PAGE_PATHS = Set.of("/", "/onboarding", "/settings");

    private final CsrfTokenRepository csrfTokenRepository;

    public CsrfTokenModelAdvice(CsrfTokenRepository csrfTokenRepository) {
        this.csrfTokenRepository = csrfTokenRepository;
    }

    @ModelAttribute
    public void addCsrfToken(HttpServletRequest request, HttpServletResponse response, Model model) {
        if (!CSRF_PAGE_PATHS.contains(request.getRequestURI())) {
            return;
        }
        Object attribute = request.getAttribute(CsrfToken.class.getName());
        if (attribute == null) {
            attribute = request.getAttribute("_csrf");
        }
        if (attribute instanceof CsrfToken csrfToken) {
            model.addAttribute("_csrf", csrfToken);
            return;
        }
        CsrfToken csrfToken = csrfTokenRepository.loadToken(request);
        if (csrfToken == null) {
            csrfToken = csrfTokenRepository.generateToken(request);
        }
        if (csrfToken != null) {
            csrfTokenRepository.saveToken(csrfToken, request, response);
            model.addAttribute("_csrf", csrfToken);
        }
    }
}
