package com.example.demo.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "subscriptions")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Subscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne
    @JoinColumn(name = "user_id", unique = true)
    private User user;

    private String planName; // "1 Month", "3 Months", etc.
    private Double amount;
    private LocalDateTime startDate;
    private LocalDateTime endDate;
    private Boolean active = true;

    @ManyToOne
    @JoinColumn(name = "payment_id")
    private Payment payment;

    private LocalDateTime createdAt = LocalDateTime.now();
}