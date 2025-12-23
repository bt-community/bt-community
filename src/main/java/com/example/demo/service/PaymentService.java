package com.example.demo.service;

import com.example.demo.entity.Payment;
import com.example.demo.entity.User;
import com.example.demo.repository.PaymentRepository;
import com.razorpay.Order;
import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import com.razorpay.Utils;
import lombok.RequiredArgsConstructor;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class PaymentService {

    private final RazorpayClient razorpayClient;
    private final PaymentRepository paymentRepository;

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
    public void verifyAndSavePayment(Map<String, String> response) throws Exception {
        String orderId = response.get("razorpay_order_id");
        String paymentId = response.get("razorpay_payment_id");
        String signature = response.get("razorpay_signature");

        // Verify signature integrity
        JSONObject options = new JSONObject();
        options.put("razorpay_order_id", orderId);
        options.put("razorpay_payment_id", paymentId);
        options.put("razorpay_signature", signature);

        boolean isValid = Utils.verifyPaymentSignature(options, keySecret);

        if (isValid) {
            Payment payment = paymentRepository.findByRazorpayOrderId(orderId)
                    .orElseThrow(() -> new RuntimeException("Order record not found in database"));

            payment.setRazorpayPaymentId(paymentId);
            payment.setRazorpaySignature(signature);
            payment.setStatus("SUCCESS");
            paymentRepository.save(payment);
        } else {
            throw new Exception("Security Alert: Invalid Payment Signature!");
        }
    }
}