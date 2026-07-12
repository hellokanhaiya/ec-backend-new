package com.ecommerce.billing;

public record VerifyRequest(
        String razorpayOrderId, String razorpayPaymentId, String razorpaySignature) {}
