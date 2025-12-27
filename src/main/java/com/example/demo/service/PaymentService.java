package com.example.demo.service;

import com.example.demo.entity.Payment;
import com.example.demo.entity.User;
import com.example.demo.repository.PaymentRepository;
import com.example.demo.repository.UserRepository;
import com.razorpay.Order;
import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import com.razorpay.Utils;
import lombok.RequiredArgsConstructor;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private final RazorpayClient razorpayClient;
    private final PaymentRepository paymentRepository;
    private final UserRepository userRepository; // ← ADD THIS


    @Value("${razorpay.key.secret}")
    private String keySecret;

    @Transactional
    public String createOrder(Double amount, User user) throws RazorpayException {
        // Razorpay expects amount in paise
        int amountInPaise = (int) (amount * 100);

        JSONObject orderRequest = new JSONObject();
        orderRequest.put("amount", amountInPaise);
        orderRequest.put("currency", "INR");
        orderRequest.put("receipt", "recp_" + System.currentTimeMillis());

        // Call Razorpay API
        Order order = razorpayClient.orders.create(orderRequest);

        // Save transaction to DB with 'CREATED' status
        Payment payment = Payment.builder()
                .razorpayOrderId(order.get("id"))
                .amount(amount)
                .currency("INR")
                .status("CREATED")
                .user(user)
                .build();

        paymentRepository.save(payment);

        return order.toString();
    }

    @Transactional
    public void verifyAndSavePayment(
            Map<String, String> response,
            User user
    ) throws Exception {

        String orderId = response.get("razorpay_order_id");
        String paymentId = response.get("razorpay_payment_id");
        String signature = response.get("razorpay_signature");

        if (orderId == null || paymentId == null || signature == null) {
            throw new RuntimeException("Incomplete payment response");
        }

        // 1. Verify signature
        JSONObject options = new JSONObject();
        options.put("razorpay_order_id", orderId);
        options.put("razorpay_payment_id", paymentId);
        options.put("razorpay_signature", signature);

        boolean isValid = Utils.verifyPaymentSignature(options, keySecret);

        if (!isValid) {
            throw new RuntimeException("Invalid payment signature");
        }

        // 2. Fetch payment from DB
        Payment payment = paymentRepository.findByRazorpayOrderId(orderId)
                .orElseThrow(() -> new RuntimeException("Payment record not found"));

        // 3. Ownership check (IMPORTANT)
        if (!payment.getUser().getId().equals(user.getId())) {
            throw new RuntimeException("Payment does not belong to this user");
        }

        user.setSubscriptionActive(true);
        user.setSubscriptionPlan(
                payment.getAmount() == 1999 ? "1 Month" :
                        payment.getAmount() == 4999 ? "3 Months" :
                                payment.getAmount() == 9999 ? "6 Months" :
                                        "12 Months"
        );
        user.setSubscriptionStart(LocalDateTime.now());
        userRepository.save(user);


    }

}