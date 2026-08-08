package com.bruce.docai.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "organizations")
@Getter
@Setter
public class Organization {

    @Id
    private String id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String slug;

    @Column(nullable = false, length = 32)
    private String status = "ACTIVE";

    @Column(name = "max_documents")
    private Integer maxDocuments;

    @Column(nullable = false)
    private Instant createdAt;

    @PrePersist
    public void onCreate() {
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    public boolean isActive() {
        return "ACTIVE".equalsIgnoreCase(status);
    }
}
