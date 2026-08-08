package com.bruce.docai.repository;

import com.bruce.docai.model.Organization;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrganizationRepository extends JpaRepository<Organization, String> {
}
