package com.bruce.docai.model;

import jakarta.persistence.*;
import lombok.Data;

@Entity
@Table(name = "knowledge_upload_payload")
@Data
public class KnowledgeUploadPayload {

    @Id
    private String knowledgeId;

    private String filename;

    private String storagePath;
}


