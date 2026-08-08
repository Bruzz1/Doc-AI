package com.bruce.docai.controller;

import com.bruce.docai.model.AgentConfig;
import com.bruce.docai.model.AgentMode;
import com.bruce.docai.model.User;
import com.bruce.docai.service.AgentConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Admin API for managing a tenant's configurable agent ({@code /admin/agents}).
 * A single configuration per organization drives every channel, so edits here take
 * effect immediately for the admin chat UI, widget, and WhatsApp.
 */
@RestController
@RequestMapping("/admin/agents")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class AgentConfigController {

    private final AgentConfigService agentConfigService;

    @GetMapping
    public AgentConfig get(Authentication authentication) {
        return agentConfigService.getForOrganization(organizationId(authentication));
    }

    @PutMapping
    public ResponseEntity<AgentConfig> save(@RequestBody AgentConfigRequest request, Authentication authentication) {
        String organizationId = organizationId(authentication);
        AgentConfig existing = agentConfigService.getForOrganization(organizationId);

        AgentConfig updated = new AgentConfig(
                existing.id(),
                organizationId,
                blankToDefault(request.name(), "Default Agent"),
                request.mode() == null ? AgentMode.SIMPLE_RAG : request.mode(),
                trimToNull(request.systemPrompt()),
                trimToNull(request.model()),
                request.temperature(),
                trimToNull(request.enabledTools()),
                request.topK(),
                request.similarityThreshold(),
                request.maxContextChars(),
                request.enabled() == null ? true : request.enabled(),
                existing.createdAt(),
                existing.updatedAt());

        return ResponseEntity.ok(agentConfigService.save(updated));
    }

    private String organizationId(Authentication authentication) {
        User user = (User) authentication.getPrincipal();
        String organizationId = user.getOrganizationId();
        if (organizationId == null || organizationId.isBlank()) {
            throw new IllegalStateException("Your account is not assigned to an organization.");
        }
        return organizationId;
    }

    private String blankToDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    @ExceptionHandler({IllegalArgumentException.class, IllegalStateException.class})
    public ResponseEntity<Map<String, String>> handleBadRequest(Exception exception) {
        return ResponseEntity.badRequest().body(Map.of("error", exception.getMessage() != null
                ? exception.getMessage() : "Unable to save the agent configuration."));
    }

    /**
     * Editable subset of {@link AgentConfig}. Null tuning fields mean "use the
     * application defaults from application.yaml".
     */
    public record AgentConfigRequest(
            String name,
            AgentMode mode,
            String systemPrompt,
            String model,
            Double temperature,
            String enabledTools,
            Integer topK,
            Double similarityThreshold,
            Integer maxContextChars,
            Boolean enabled) {
    }
}
