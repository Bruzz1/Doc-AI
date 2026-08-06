package com.bruce.docai.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.Instant;

@Entity
@Table(name = "knowledge_entry")
@Data
public class KnowledgeEntry {

	@Id
	private String knowledgeId;

	@Column(nullable = false)
	private String filename;

	private String contentType;

	@Column(nullable = false)
	private String organizationId;

	@Column(nullable = false)
	private String uploadedBy;

	@Column(nullable = false)
	private Instant createdAt;

	@PrePersist
	void onCreate() {
		if (createdAt == null) {
			createdAt = Instant.now();
		}
	}
}


