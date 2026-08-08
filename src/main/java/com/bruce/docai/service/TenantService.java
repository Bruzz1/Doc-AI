package com.bruce.docai.service;

import com.bruce.docai.model.Organization;
import com.bruce.docai.repository.OrganizationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Tenant lifecycle checks shared across services: an organization must exist and
 * be active before it can be used for invites, uploads, or retrieval.
 */
@Service
@RequiredArgsConstructor
public class TenantService {

    private final OrganizationRepository organizationRepository;

    public Organization requireActive(String organizationId) {
        if (organizationId == null || organizationId.isBlank()) {
            throw new IllegalArgumentException("No organization was provided.");
        }
        Organization organization = organizationRepository.findById(organizationId)
                .orElseThrow(() -> new IllegalArgumentException("Organization '" + organizationId + "' does not exist."));
        if (!organization.isActive()) {
            throw new IllegalArgumentException("Organization '" + organizationId + "' is not active.");
        }
        return organization;
    }
}
