package com.bruce.docai.repository;

import com.bruce.docai.model.KnowledgeUploadPayload;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KnowledgeUploadPayloadRepository extends JpaRepository<KnowledgeUploadPayload, String> {
}

