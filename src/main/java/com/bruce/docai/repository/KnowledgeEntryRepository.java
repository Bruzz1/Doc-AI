package com.bruce.docai.repository;

import com.bruce.docai.model.KnowledgeEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface KnowledgeEntryRepository extends JpaRepository<KnowledgeEntry, String> {

	List<KnowledgeEntry> findByOrganizationIdOrderByCreatedAtDesc(String organizationId);

	Optional<KnowledgeEntry> findByKnowledgeIdAndOrganizationId(String knowledgeId, String organizationId);
}


