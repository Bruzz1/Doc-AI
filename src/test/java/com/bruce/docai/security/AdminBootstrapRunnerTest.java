package com.bruce.docai.security;

import com.bruce.docai.model.User;
import com.bruce.docai.repository.UserRepository;
import com.bruce.docai.security.config.AdminBootstrapRunner;
import com.bruce.docai.security.config.SecurityProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminBootstrapRunnerTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Test
    void createsAdminWhenBootstrapIsEnabledAndNoAdminExists() throws Exception {
        SecurityProperties properties = new SecurityProperties(
                new SecurityProperties.Jwt("1234567890123456789012345678901234567890", 300, 86400, 604800),
                new SecurityProperties.Cookie(true, "Lax", ""),
                new SecurityProperties.Bootstrap(true, "admin@example.com", "Secret123!", "Root Admin", "org-1")
        );

        when(userRepository.countByRole("ADMIN")).thenReturn(0L);
        when(userRepository.existsByEmail("admin@example.com")).thenReturn(false);
        when(passwordEncoder.encode("Secret123!")).thenReturn("encoded");

        AdminBootstrapRunner runner = new AdminBootstrapRunner(properties, userRepository, passwordEncoder);
        runner.run(null);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());

        User saved = captor.getValue();
        assertEquals("admin@example.com", saved.getEmail());
        assertEquals("ADMIN", saved.getRole());
        assertEquals("encoded", saved.getPassword());
        assertEquals(Boolean.TRUE, saved.getMustChangePassword());
        assertEquals("org-1", saved.getOrganizationId());
    }

    @Test
    void skipsCreationWhenBootstrapDisabled() throws Exception {
        SecurityProperties properties = new SecurityProperties(
                new SecurityProperties.Jwt("1234567890123456789012345678901234567890", 300, 86400, 604800),
                new SecurityProperties.Cookie(true, "Lax", ""),
                new SecurityProperties.Bootstrap(false, "admin@example.com", "Secret123!", "Root Admin", "org-1")
        );

        AdminBootstrapRunner runner = new AdminBootstrapRunner(properties, userRepository, passwordEncoder);
        runner.run(null);

        verify(userRepository, never()).save(any());
    }
}


