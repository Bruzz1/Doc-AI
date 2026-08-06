package com.bruce.docai.security.config;

import com.bruce.docai.model.User;
import com.bruce.docai.repository.UserRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
@Slf4j
public class AdminBootstrapRunner implements ApplicationRunner {

    private final SecurityProperties securityProperties;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AdminBootstrapRunner(
            SecurityProperties securityProperties,
            UserRepository userRepository,
            PasswordEncoder passwordEncoder
    ) {
        this.securityProperties = securityProperties;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        SecurityProperties.Bootstrap bootstrap = securityProperties.bootstrap();
        if (bootstrap == null || !bootstrap.enabled()) {
            return;
        }

        if (isBlank(bootstrap.email()) || isBlank(bootstrap.password())) {
            log.warn("Bootstrap admin is enabled but email/password is missing; skipping admin creation");
            return;
        }

        if (userRepository.countByRole("ADMIN") > 0) {
            log.info("Admin user already exists, skipping bootstrap admin creation");
            return;
        }

        String normalizedEmail = bootstrap.email().trim().toLowerCase();
        if (userRepository.existsByEmail(normalizedEmail)) {
            log.info("Bootstrap email already exists, skipping bootstrap admin creation");
            return;
        }

        User admin = new User();
        admin.setEmail(normalizedEmail);
        admin.setPassword(passwordEncoder.encode(bootstrap.password()));
        admin.setName(isBlank(bootstrap.name()) ? "System Admin" : bootstrap.name().trim());
        admin.setRole("ADMIN");
        admin.setOrganizationId(isBlank(bootstrap.organizationId()) ? "default-org" : bootstrap.organizationId().trim());
        admin.setMustChangePassword(true);
        admin.setPasswordChangedAt(Instant.now());

        userRepository.save(admin);
        log.warn("Bootstrap admin user created for {}. Sign in and rotate password immediately.", normalizedEmail);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}

