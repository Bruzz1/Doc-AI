package com.bruce.docai.repository;

import com.bruce.docai.model.InviteToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface InviteTokenRepository extends JpaRepository<InviteToken, String> {

    Optional<InviteToken> findByTokenHash(String tokenHash);
}

