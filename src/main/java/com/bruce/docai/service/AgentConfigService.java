package com.bruce.docai.service;

import com.bruce.docai.model.AgentConfig;
import com.bruce.docai.model.AgentMode;
import com.bruce.docai.repository.AgentConfigRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Loads and persists the single per-tenant {@link AgentConfig}. When a tenant has
 * no stored configuration yet (e.g. an organization created after the V4 seed ran),
 * a safe in-memory {@link AgentMode#SIMPLE_RAG} default is returned so runtime chat
 * always has something to run — this preserves the original behavior until an admin
 * customizes the agent.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AgentConfigService {

    private final AgentConfigRepository agentConfigRepository;
    private final TenantService tenantService;

    /**
     * Resolve the effective agent configuration for a tenant, never returning null.
     */
    public AgentConfig getForOrganization(String organizationId) {
        tenantService.requireActive(organizationId);
        return agentConfigRepository.findByOrganizationId(organizationId)
                .orElseGet(() -> defaultConfig(organizationId));
    }

    /**
     * Create or update the tenant's agent configuration (upsert semantics).
     */
    public AgentConfig save(AgentConfig config) {
        tenantService.requireActive(config.organizationId());
        if (agentConfigRepository.findByOrganizationId(config.organizationId()).isPresent()) {
            agentConfigRepository.update(config);
        } else {
            agentConfigRepository.insert(config);
        }
        return agentConfigRepository.findByOrganizationId(config.organizationId())
                .orElseThrow(() -> new IllegalStateException("Agent configuration was not persisted."));
    }

    private AgentConfig defaultConfig(String organizationId) {
        log.debug("No stored agent config for organization {}; using SIMPLE_RAG default.", organizationId);
        return new AgentConfig(
                null,
                organizationId,
                "Default Agent",
                AgentMode.SIMPLE_RAG,
                null, null, null, null, null, null, null,
                true,
                null, null);
    }
}
