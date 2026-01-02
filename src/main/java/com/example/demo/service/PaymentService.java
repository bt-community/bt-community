package com.example.demo.service;

import com.example.demo.entity.Payment;
import com.example.demo.entity.Subscription;
import com.example.demo.entity.User;
import com.example.demo.repository.PaymentRepository;
import com.example.demo.repository.SubscriptionRepository;
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
    private final SubscriptionRepository subscriptionRepository;
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

        // 4. Update payment status
        payment.setRazorpayPaymentId(paymentId);
        payment.setRazorpaySignature(signature);
        payment.setStatus("SUCCESS");

        paymentRepository.save(payment);
        // 5. Create/Update Subscription
        createOrUpdateSubscription(payment, user);
    }
    private void createOrUpdateSubscription(Payment payment, User user) {
        // Deactivate old subscription if exists
        subscriptionRepository.findByUserAndActiveTrue(user)
                .ifPresent(sub -> {
                    sub.setActive(false);
                    subscriptionRepository.save(sub);
                });

        // Determine plan name and duration from amount
        String planName;
        int durationMonths;

        double amount = payment.getAmount();
        if (amount == 1999.0) {
            planName = "1 Month";
            durationMonths = 1;
        } else if (amount == 4999.0) {
            planName = "3 Months";
            durationMonths = 3;
        } else if (amount == 9999.0) {
            planName = "6 Months";
            durationMonths = 6;
        } else if (amount == 17999.0) {
            planName = "12 Months";
            durationMonths = 12;
        } else {
            throw new RuntimeException("Invalid plan amount");
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime endDate = now.plusMonths(durationMonths);

        Subscription subscription = Subscription.builder()
                .user(user)
                .planName(planName)
                .amount(amount)
                .startDate(now)
                .endDate(endDate)
                .active(true)
                .payment(payment)
                .build();

        subscriptionRepository.save(subscription);
    }
}
