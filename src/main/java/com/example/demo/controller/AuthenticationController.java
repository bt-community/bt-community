package com.example.demo.controller;

import com.example.demo.entity.User;
import com.example.demo.repository.UserRepository;
import com.example.demo.service.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthenticationController {

    private final AuthenticationManager authenticationManager;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    @PostMapping("/register")
    public ResponseEntity<String> register(@RequestBody User request) {
        // Check if user exists before trying to save (optional but good practice)
        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            return ResponseEntity.badRequest().body("Email already in use");
        }
        request.setPassword(passwordEncoder.encode(request.getPassword()));
        userRepository.save(request);
        return ResponseEntity.ok("Registration Successful");
    }

    @PostMapping("/login")
    public ResponseEntity<?> authenticate(@RequestBody User request) {
        try {
            authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.getEmail(),
                            request.getPassword()
                    )
            );
        } catch (Exception e) {
            // IMPORTANT: prevent Spring Security from returning raw 403
            return ResponseEntity
                    .status(401)
                    .body("Invalid email or password");
        }

        var user = userRepository.findByEmail(request.getEmail())
                .orElseThrow();

        Map<String, Object> extraClaims = new HashMap<>();
        extraClaims.put("fullName", user.getFullName());
        extraClaims.put("phoneNumber", user.getPhoneNumber());

        String token = jwtService.generateToken(extraClaims, user);

        return ResponseEntity.ok(token);
    }


    // --- NEW ENDPOINT: Forgot Password ---
    @PostMapping("/forgot-password")
    public ResponseEntity<String> resetPassword(@RequestBody Map<String, String> request) {
        String email = request.get("email");
        String newPassword = request.get("password");

        if (email == null || newPassword == null || newPassword.isEmpty()) {
            return ResponseEntity.badRequest().body("Email and new password are required");
        }

        var user = userRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("User with this email not found"));

        // Update and hash the new password
        user.setPassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        return ResponseEntity.ok("Password updated successfully");
    }
}