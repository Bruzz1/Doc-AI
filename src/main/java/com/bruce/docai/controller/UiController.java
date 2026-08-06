package com.bruce.docai.controller;

import org.springframework.stereotype.Controller;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class UiController {

    @GetMapping("/")
    public String root() {
        return "redirect:/login";
    }

    @GetMapping("/login")
    public String login(Authentication authentication) {
        if (authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken)) {
            return "redirect:/app";
        }
        return "login";
    }

    @GetMapping("/accept-invite")
    public String acceptInvite(Authentication authentication) {
        // Redirect to app if already authenticated
        if (authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken)) {
            return "redirect:/app";
        }
        return "accept-invite";
    }

    @GetMapping("/app")
    public String index() {
        return "index";
    }

    @GetMapping("/change-password")
    public String changePassword() {
        return "change-password";
    }

    @GetMapping("/admin/invites")
    @PreAuthorize("hasRole('ADMIN')")
    public String adminInvites() {
        return "admin-invites";
    }
}
