package com.example.demo.controller;

import com.example.demo.entity.User;
import com.example.demo.repository.UserRepository;
import com.example.demo.service.PaymentService;
import com.razorpay.RazorpayException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/payment")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;
    private final UserRepository userRepository;

    @PostMapping("/create-order")
    public ResponseEntity<?> createOrder(@RequestBody Map<String, Object> requestData) {
        try {
            // Get user email from JWT context
            String email = SecurityContextHolder.getContext().getAuthentication().getName();
            User user = userRepository.findByEmail(email)
                    .orElseThrow(() -> new RuntimeException("User not found"));

            Double amount = Double.parseDouble(requestData.get("amount").toString());
            String orderJson = paymentService.createOrder(amount, user);

            return ResponseEntity.ok(orderJson);
        } catch (RazorpayException e) {
            return ResponseEntity.internalServerError().body("Razorpay Error: " + e.getMessage());
        }
    }

    @PostMapping("/verify")
    public ResponseEntity<?> verifyPayment(@RequestBody Map<String, String> paymentResponse) {
        try {
            paymentService.verifyAndSavePayment(paymentResponse);
            return ResponseEntity.ok(Map.of("message", "Payment processed successfully"));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Verification Failed: " + e.getMessage());
        }
    }
}