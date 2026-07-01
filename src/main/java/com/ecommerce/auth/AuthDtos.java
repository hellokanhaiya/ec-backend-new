package com.ecommerce.auth;

import java.time.Instant;
import java.util.List;

record ApiResponse<T>(boolean success, String message, T data) {}

record AuthLookupRequest(String channel, String identifier, String countryCode) {}

record AuthLookupData(
        String publicUserId,
        AuthAudience audience,
        boolean existing,
        AuthAccountState accountState,
        String channel,
        String maskedIdentifier,
        List<String> allowedChannels,
        String message,
        boolean canRequestOtp,
        Integer attemptsLeft,
        Integer resendAfterSeconds,
        Instant lockedUntil) {}

record OtpRequestPayload(
        String purpose,
        String channel,
        String identifier,
        String displayName,
        String countryCode) {}

record OtpRequestData(
        Long otpRequestId,
        AuthAudience audience,
        AuthPurpose purpose,
        AuthChannel channel,
        AuthAccountState accountState,
        String maskedDestination,
        String message,
        boolean canRequestOtp,
        Integer attemptsLeft,
        Integer resendAfterSeconds,
        Instant lockedUntil) {}

record OtpVerifyPayload(Long otpRequestId, String code) {}

record AuthSessionData(
        Long userId,
        String publicUserId,
        String orgId,
        String storeId,
        String accessToken,
        String refreshToken,
        Instant expiresAt,
        AuthAudience audience,
        AuthAccountState accountState,
        boolean onboardingRequired) {}

record OAuthStartData(
        String provider,
        boolean configured,
        String authorizationUrl,
        String message) {}

record OAuthCallbackPayload(String code, String state, String redirectUri) {}

record OAuthCallbackData(
        String provider,
        boolean configured,
        String message,
        String accessToken,
        String refreshToken) {}
