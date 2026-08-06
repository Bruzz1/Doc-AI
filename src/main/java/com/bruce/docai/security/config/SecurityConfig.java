package com.bruce.docai.security.config;

import com.bruce.docai.security.filter.JwtAuthFilter;
import com.bruce.docai.security.filter.MustChangePasswordFilter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import jakarta.servlet.http.HttpServletResponse;

import static org.springframework.http.HttpHeaders.ACCEPT;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@EnableConfigurationProperties(SecurityProperties.class)
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final MustChangePasswordFilter mustChangePasswordFilter;

    public SecurityConfig(JwtAuthFilter jwtAuthFilter, MustChangePasswordFilter mustChangePasswordFilter) {
        this.jwtAuthFilter = jwtAuthFilter;
        this.mustChangePasswordFilter = mustChangePasswordFilter;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http.csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/", "/login", "/accept-invite", "/error").permitAll()
                        .requestMatchers("/style.css", "/favicon.ico").permitAll()
                        .requestMatchers("/auth/login", "/auth/refresh", "/auth/logout", "/auth/accept-invite").permitAll()
                        .requestMatchers("/actuator/**").permitAll()
                        .anyRequest().authenticated()
                )
                .exceptionHandling(exception -> exception.authenticationEntryPoint((request, response, ex) -> {
                    boolean expectsHtml = request.getHeader(ACCEPT) != null && request.getHeader(ACCEPT).contains("text/html");
                    String uri = request.getRequestURI();
                    boolean isApiRequest = uri.startsWith("/chat")
                            || uri.startsWith("/faqs")
                            || uri.startsWith("/upload")
                            || uri.startsWith("/auth/change-password")
                            || uri.startsWith("/auth/me")
                            || uri.startsWith("/auth/invites");

                    if (expectsHtml && !isApiRequest) {
                        response.sendRedirect("/login");
                    } else {
                        response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
                    }
                }))
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(mustChangePasswordFilter, JwtAuthFilter.class);
        return  http.build();

    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

}

