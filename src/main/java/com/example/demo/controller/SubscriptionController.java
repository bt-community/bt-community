package com.example.demo.controller;

import com.example.demo.entity.Subscription;
import com.example.demo.entity.User;
import com.example.demo.repository.SubscriptionRepository;
import com.example.demo.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/subscription")
@RequiredArgsConstructor
public class SubscriptionController {

    private final SubscriptionRepository subscriptionRepository;
    private final UserRepository userRepository;

    @GetMapping("/status")
    public ResponseEntity<?> getSubscriptionStatus() {
        try {
            String email = SecurityContextHolder.getContext().getAuthentication().getName();
            User user = userRepository.findByEmail(email)
                    .orElseThrow(() -> new RuntimeException("User not found"));

            return subscriptionRepository.findByUserAndActiveTrue(user)
                    .map(sub -> {
                        // Check if subscription is still valid
                        boolean isActive = sub.getEndDate().isAfter(LocalDateTime.now());

                        if (!isActive && sub.getActive()) {
                            // Deactivate expired subscription
                            sub.setActive(false);
                            subscriptionRepository.save(sub);
                        }

                        Map<String, Object> response = new HashMap<>();
                        response.put("active", isActive);
                        response.put("plan", sub.getPlanName());
                        response.put("startDate", sub.getStartDate());
                        response.put("endDate", sub.getEndDate());
                        return ResponseEntity.ok(response);
                    })
                    .orElse(ResponseEntity.ok(Map.of("active", false)));

        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Error: " + e.getMessage());
        }
    }
}