package com.example.demo.controller;

import com.example.demo.entity.Review;
import com.example.demo.repository.ReviewRepository;
import com.example.demo.service.JwtService;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/reviews")
@RequiredArgsConstructor
public class ReviewController {

    private final ReviewRepository reviewRepository;
    private final JwtService jwtService;

    // POST: Needs Token, Fetches name from Token
    @PostMapping
    public ResponseEntity<Review> postReview(
            @RequestHeader("Authorization") String token,
            @RequestBody Review reviewRequest) {

        // Remove "Bearer " prefix
        String jwt = token.substring(7);

        // Extract the "fullName" claim we added to the token earlier
        String fullName = jwtService.extractClaim(jwt, claims -> claims.get("fullName", String.class));

        Review review = Review.builder()
                .userName(fullName)
                .rating(reviewRequest.getRating())
                .massage(reviewRequest.getMassage())
                .build();

        return ResponseEntity.ok(reviewRepository.save(review));
    }

    // GET: Public, no token needed
    @GetMapping
    public ResponseEntity<List<Review>> getAllReviews() {
        return ResponseEntity.ok(reviewRepository.findAllByOrderByCreatedAtDesc());
    }
}