package com.bruce.docai.model;

import jakarta.persistence.*;
import lombok.Data;

import java.time.Instant;

@Entity
@Table(name = "users")
@Data
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(unique = true)
    private String email;

    private String password;

    private String name;

    // Saas foundation - tenant identification - by Rj
    private String organizationId;

    private String role;

    @Column(nullable = true, columnDefinition = "boolean default false")
    private Boolean mustChangePassword = false;

    private Instant passwordChangedAt;
}
