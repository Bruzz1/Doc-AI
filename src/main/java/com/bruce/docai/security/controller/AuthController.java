package com.bruce.docai.security.controller;

import com.bruce.docai.model.InviteToken;
import com.bruce.docai.model.RefreshToken;
import com.bruce.docai.model.User;
import com.bruce.docai.repository.InviteTokenRepository;
import com.bruce.docai.repository.UserRepository;
import com.bruce.docai.security.dto.InviteTokenResponse;
import com.bruce.docai.security.service.AuthCookieService;
import com.bruce.docai.security.service.InviteService;
import com.bruce.docai.security.service.JwtService;
import com.bruce.docai.security.service.RefreshTokenService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserRepository userRepository;

    private final InviteTokenRepository inviteTokenRepository;

    private final PasswordEncoder passwordEncoder;

    private final JwtService jwtService;

    private final InviteService inviteService;

    private final RefreshTokenService refreshTokenService;

    private final AuthCookieService authCookieService;

    private final com.bruce.docai.service.TenantService tenantService;

    private final com.bruce.docai.service.AuditService auditService;

    @PostMapping("/accept-invite")
    public ResponseEntity<Map<String, String>> acceptInvite(
            @RequestBody AcceptInviteRequest request,
            HttpServletResponse response
    ) {

        InviteToken invite = inviteService.consumeInvite(request.inviteToken());

        if (userRepository.findByEmail(invite.getEmail()).isPresent()) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(Map.of("error", "User already exists for this invite email"));
        }

        User user = new User();
        user.setEmail(invite.getEmail());
        user.setPassword(passwordEncoder.encode(request.password()));
        user.setName(request.name());
        user.setRole(invite.getRole());
        user.setOrganizationId(invite.getOrganizationId());
        user.setMustChangePassword(false);
        user.setPasswordChangedAt(Instant.now());
        userRepository.save(user);

        issueSessionCookies(user, response);

        return ResponseEntity.ok(Map.of("message", "Invite accepted"));
    }

    @PostMapping("/login")
    public ResponseEntity<Map<String, Object>> login(
            @RequestBody LoginRequest request,
            HttpServletResponse response
    ) {
        User user = userRepository.findByEmail(request.email().trim().toLowerCase())
                .orElseThrow(() -> new IllegalArgumentException("Invalid username/password"));

        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new IllegalArgumentException("Invalid username/password");
        }

        issueSessionCookies(user, response);
        return ResponseEntity.ok(Map.of(
                "message", "Logged in",
                "mustChangePassword", user.getMustChangePassword() == Boolean.TRUE
        ));
    }

    @PostMapping("/refresh")
    public ResponseEntity<Map<String, String>> refresh(HttpServletRequest request, HttpServletResponse response) {
        String refreshRawToken = authCookieService.readCookie(request, AuthCookieService.REFRESH_TOKEN_COOKIE)
                .orElseThrow(() -> new IllegalArgumentException("Refresh token is missing"));

        RefreshToken refreshToken = refreshTokenService.findValidByRawToken(refreshRawToken)
                .orElseThrow(() -> new IllegalArgumentException("Refresh token is invalid or expired"));

        String newAccessToken = jwtService.generateAccessToken(refreshToken.getUser());
        String newRefreshToken = refreshTokenService.rotate(refreshToken);

        authCookieService.setAccessTokenCookie(response, newAccessToken);
        authCookieService.setRefreshTokenCookie(response, newRefreshToken);

        return ResponseEntity.ok(Map.of("message", "Session refreshed"));
    }

    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout(HttpServletRequest request, HttpServletResponse response) {
        authCookieService.readCookie(request, AuthCookieService.REFRESH_TOKEN_COOKIE)
                .ifPresent(refreshTokenService::revokeByRawToken);

        authCookieService.clearAuthCookies(response);
        return ResponseEntity.ok(Map.of("message", "Logged out"));
    }

    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> me(Authentication authentication) {
        User principal = (User) authentication.getPrincipal();
        return ResponseEntity.ok(Map.of(
                "email", principal.getEmail(),
                "name", principal.getName(),
                "role", principal.getRole(),
                "organizationId", principal.getOrganizationId(),
                "mustChangePassword", principal.getMustChangePassword() == Boolean.TRUE
        ));
    }

    @PostMapping("/change-password")
    public ResponseEntity<Map<String, String>> changePassword(
            @RequestBody ChangePasswordRequest request,
            Authentication authentication,
            HttpServletResponse response
    ) {
        User principal = (User) authentication.getPrincipal();
        User user = userRepository.findById(principal.getId())
                .orElseThrow(() -> new IllegalArgumentException("User not found"));

        if (!passwordEncoder.matches(request.currentPassword(), user.getPassword())) {
            throw new IllegalArgumentException("Current password is invalid");
        }

        if (request.newPassword() == null || request.newPassword().length() < 8) {
            throw new IllegalArgumentException("New password must be at least 8 characters");
        }

        user.setPassword(passwordEncoder.encode(request.newPassword()));
        user.setMustChangePassword(false);
        user.setPasswordChangedAt(Instant.now());
        userRepository.save(user);

        refreshTokenService.revokeAllForUser(user.getId());
        issueSessionCookies(user, response);
        return ResponseEntity.ok(Map.of("message", "Password updated"));
    }

    @PostMapping("/invites")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> createInvite(
            @RequestBody CreateInviteRequest request,
            Authentication authentication
    ) {
        User admin = (User) authentication.getPrincipal();

        // Tenant isolation: an admin may only invite users into their own organization.
        String requestedOrg = request.organizationId();
        if (requestedOrg != null && !requestedOrg.isBlank()
                && !requestedOrg.equals(admin.getOrganizationId())) {
            throw new IllegalArgumentException("You can only invite users into your own organization.");
        }
        String organizationId = admin.getOrganizationId();
        tenantService.requireActive(organizationId);

        String role = request.role() == null || request.role().isBlank()
                ? "USER"
                : request.role().toUpperCase();

        String inviteToken = inviteService.createInvite(
                request.email(),
                organizationId,
                role,
                admin.getEmail()
        );

        auditService.record(organizationId, admin.getEmail(), "INVITE_CREATE", request.email(), "role=" + role);

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "inviteToken", inviteToken,
                "email", request.email(),
                "organizationId", organizationId,
                "role", role
        ));
    }

    @PostMapping("/invites/{id}/regenerate")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> regenerateInvite(
            @PathVariable String id,
            Authentication authentication
    ) {
        User admin = (User) authentication.getPrincipal();

        InviteToken existing = inviteTokenRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Invite not found"));

        if (existing.getUsedAt() != null) {
            throw new IllegalArgumentException("Cannot regenerate an already-used invite");
        }

        // Delete old token and create a fresh one with the same details
        inviteTokenRepository.delete(existing);

        String newToken = inviteService.createInvite(
                existing.getEmail(),
                existing.getOrganizationId(),
                existing.getRole(),
                admin.getEmail()
        );

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "inviteToken", newToken,
                "email", existing.getEmail(),
                "organizationId", existing.getOrganizationId(),
                "role", existing.getRole()
        ));
    }

    @DeleteMapping("/invites/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> cancelInvite(@PathVariable String id) {
        InviteToken existing = inviteTokenRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Invite not found"));

        if (existing.getUsedAt() != null) {
            throw new IllegalArgumentException("Cannot cancel an already-used invite");
        }

        inviteTokenRepository.delete(existing);
        return ResponseEntity.ok(Map.of("message", "Invite cancelled"));
    }

    @GetMapping("/invites")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<InviteTokenResponse>> listInvites() {
        List<InviteToken> invites = inviteTokenRepository.findAll();
        List<InviteTokenResponse> response = invites.stream()
                .map(InviteTokenResponse::fromModel)
                .toList();
        return ResponseEntity.ok(response);
    }

    private void issueSessionCookies(User user, HttpServletResponse response) {
        String accessToken = jwtService.generateAccessToken(user);
        String refreshToken = refreshTokenService.create(user);
        authCookieService.setAccessTokenCookie(response, accessToken);
        authCookieService.setRefreshTokenCookie(response, refreshToken);
    }

    public record LoginRequest(String email, String password) {}

    public record AcceptInviteRequest(String inviteToken, String name, String password) {}

    public record CreateInviteRequest(String email, String organizationId, String role) {}

    public record ChangePasswordRequest(String currentPassword, String newPassword) {}

    @org.springframework.web.bind.annotation.ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> handleBadRequest(IllegalArgumentException exception) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", exception.getMessage()));
    }
}

