package com.bruce.docai.security;

import com.bruce.docai.model.User;
import com.bruce.docai.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserRepository userRepository;

    private final PasswordEncoder passwordEncoder;

    private final JwtService jwtService;

    @PostMapping("/register")
    public String register(@RequestBody User request) {

        if(userRepository.findByEmail(request.getEmail()).isPresent()) {
            return "Email already exists";
        }

        User user = new User();
        user.setEmail(request.getEmail());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setName(request.getName());
        user.setRole("USER");

        //TODO: Improve later
        user.setOrganizationId(UUID.randomUUID().toString());

        userRepository.save(user);

        return "Registered";
    }

    @PostMapping("/login")
    public Map<String, String> login(@RequestBody User request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (!passwordEncoder.matches(request.getPassword(),user.getPassword())) {
            throw new RuntimeException("Invalid Username/Password");
        }

        String token = jwtService.generateToken(user);
        return Map.of("token", token);
    }
}
