package com.ecommerce.auth;

import com.ecommerce.auth.entity.AdminAuthSession;
import com.ecommerce.auth.entity.AdminAuthUser;
import com.ecommerce.auth.entity.AdminOtpRequest;
import com.ecommerce.auth.entity.AbstractAuthSession;
import com.ecommerce.auth.entity.AbstractOtpRequest;
import com.ecommerce.auth.entity.ConsumerAuthSession;
import com.ecommerce.auth.entity.ConsumerAuthUser;
import com.ecommerce.auth.entity.ConsumerOtpRequest;
import com.ecommerce.auth.entity.AbstractAuthUser;
import com.ecommerce.auth.repository.AdminAuthSessionRepository;
import com.ecommerce.auth.repository.AdminAuthUserRepository;
import com.ecommerce.auth.repository.AdminOtpRequestRepository;
import com.ecommerce.auth.repository.ConsumerAuthSessionRepository;
import com.ecommerce.auth.repository.ConsumerAuthUserRepository;
import com.ecommerce.auth.repository.ConsumerOtpRequestRepository;
import com.ecommerce.store.StoreProfileRepository;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.GONE;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.TOO_MANY_REQUESTS;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

@Service
@Transactional
public class AuthService {
    private static final Logger LOGGER = LoggerFactory.getLogger(AuthService.class);
    private static final int MAX_OTP_ATTEMPTS = 5;
    private static final int OTP_TTL_MINUTES = 10;
    private static final int OTP_LOCK_MINUTES = 15;
    private static final int SESSION_TTL_DAYS = 7;

    private final AdminAuthUserRepository adminAuthUserRepository;
    private final ConsumerAuthUserRepository consumerAuthUserRepository;
    private final AdminOtpRequestRepository adminOtpRequestRepository;
    private final ConsumerOtpRequestRepository consumerOtpRequestRepository;
    private final AdminAuthSessionRepository adminAuthSessionRepository;
    private final ConsumerAuthSessionRepository consumerAuthSessionRepository;
    private final OtpDeliveryService otpDeliveryService;
    private final StoreProfileRepository storeProfileRepository;

    public AuthService(
            AdminAuthUserRepository adminAuthUserRepository,
            ConsumerAuthUserRepository consumerAuthUserRepository,
            AdminOtpRequestRepository adminOtpRequestRepository,
            ConsumerOtpRequestRepository consumerOtpRequestRepository,
            AdminAuthSessionRepository adminAuthSessionRepository,
            ConsumerAuthSessionRepository consumerAuthSessionRepository,
            OtpDeliveryService otpDeliveryService,
            StoreProfileRepository storeProfileRepository) {
        this.adminAuthUserRepository = adminAuthUserRepository;
        this.consumerAuthUserRepository = consumerAuthUserRepository;
        this.adminOtpRequestRepository = adminOtpRequestRepository;
        this.consumerOtpRequestRepository = consumerOtpRequestRepository;
        this.adminAuthSessionRepository = adminAuthSessionRepository;
        this.consumerAuthSessionRepository = consumerAuthSessionRepository;
        this.otpDeliveryService = otpDeliveryService;
        this.storeProfileRepository = storeProfileRepository;
    }

    public AuthLookupData lookup(AuthAudience audience, AuthLookupRequest request) {
        AuthChannel channel = parseChannel(request.channel());
        String normalizedIdentifier = normalizeAndValidate(channel, request.identifier());
        Optional<? extends AbstractAuthUser> user = findUser(audience, channel, normalizedIdentifier);

        AuthAccountState state = user.map(this::resolveState).orElse(AuthAccountState.NEW);
        String message = switch (state) {
            case EXISTING -> "Account found. Please sign in.";
            case PENDING -> "Account setup is in progress. Please continue verification.";
            case NEW -> "No account found. Please sign up.";
        };

        Integer attemptsLeft = resolveLatestOtpRequest(audience, channel, normalizedIdentifier, AuthPurpose.SIGNIN)
                .map(requestData -> Math.max(0, MAX_OTP_ATTEMPTS - requestData.getAttempts()))
                .orElse(MAX_OTP_ATTEMPTS);
        Integer resendAfterSeconds = resolveLatestOtpRequest(audience, channel, normalizedIdentifier, AuthPurpose.SIGNIN)
                .map(requestData -> secondsUntil(requestData.getResendAvailableAt()))
                .orElse(0);
        Instant lockedUntil = resolveLatestOtpRequest(audience, channel, normalizedIdentifier, AuthPurpose.SIGNIN)
                .map(AbstractOtpRequest::getLockedUntil)
                .orElse(null);

        return new AuthLookupData(
                user.map(AbstractAuthUser::getPublicId).orElse(null),
                audience,
                state != AuthAccountState.NEW,
                state,
                channel.name(),
                AuthSupport.maskIdentifier(channel, normalizedIdentifier),
                List.of("EMAIL", "PHONE", "WHATSAPP"),
                message,
                lockedUntil == null || Instant.now().isAfter(lockedUntil),
                attemptsLeft,
                resendAfterSeconds,
                lockedUntil);
    }

    public OtpRequestData requestOtp(AuthAudience audience, OtpRequestPayload payload) {
        AuthPurpose purpose = parsePurpose(payload.purpose());
        AuthChannel channel = parseChannel(payload.channel());
        String normalizedIdentifier = normalizeAndValidate(channel, payload.identifier());
        String identifier = payload.identifier().trim();
        Optional<? extends AbstractAuthUser> existingUser = findUser(audience, channel, normalizedIdentifier);

        if (purpose == AuthPurpose.SIGNUP && existingUser.isPresent() && existingUser.get().getStatus() == AuthUserStatus.ACTIVE) {
            throw new ResponseStatusException(CONFLICT, "An account already exists for this identifier. Please sign in.");
        }

        if (purpose == AuthPurpose.SIGNIN && existingUser.isEmpty()) {
            throw new ResponseStatusException(NOT_FOUND, "No account found for this identifier. Please sign up first.");
        }

        AbstractAuthUser user;
        if (existingUser.isPresent()) {
            user = existingUser.get();
        } else {
            user = createPendingUser(audience, channel, normalizedIdentifier, payload.displayName());
        }
        AuthAccountState accountState = resolveState(user);

        Optional<? extends AbstractOtpRequest> latestRequest = resolveLatestOtpRequest(audience, channel, normalizedIdentifier, purpose);
        Instant now = Instant.now();
        if (latestRequest.isPresent()) {
            AbstractOtpRequest existingRequest = latestRequest.get();
            if (isLockActive(existingRequest, now)) {
                throw new ResponseStatusException(TOO_MANY_REQUESTS, lockMessage(existingRequest, now));
            }
            if (isResendCoolingDown(existingRequest, now)) {
                throw new ResponseStatusException(TOO_MANY_REQUESTS, resendMessage(existingRequest, now));
            }
            if (existingRequest.getStatus() == OtpRequestStatus.PENDING && existingRequest.getRequestCount() >= 5) {
                existingRequest.setStatus(OtpRequestStatus.LOCKED);
                existingRequest.setLockedUntil(now.plus(OTP_LOCK_MINUTES, ChronoUnit.MINUTES));
                saveOtpRequest(audience, existingRequest);
                throw new ResponseStatusException(TOO_MANY_REQUESTS, lockMessage(existingRequest, now));
            }
            if (existingRequest.getStatus() == OtpRequestStatus.PENDING) {
                existingRequest.setStatus(OtpRequestStatus.EXPIRED);
                saveOtpRequest(audience, existingRequest);
            }
        }

        AbstractOtpRequest otpRequest = createOtpRequest(audience, purpose, channel, identifier, normalizedIdentifier, user.getId());
        int requestCount = latestRequest
                .filter(requestData -> requestData.getStatus() == OtpRequestStatus.PENDING)
                .map(AbstractOtpRequest::getRequestCount)
                .orElse(0) + 1;
        otpRequest.setRequestCount(requestCount);
        otpRequest.setResendAvailableAt(now.plusSeconds(AuthSupport.resendDelaySeconds(requestCount)));
        String otpCode = AuthSupport.generateOtp();
        otpRequest.setOtpHash(AuthSupport.hashOtp(String.valueOf(otpRequest.getId()), otpCode));
        otpRequest.setDeliveryStatus("SENT");
        saveOtpRequest(audience, otpRequest);
        deliverOtp(channel, normalizedIdentifier, otpCode);

        return new OtpRequestData(
                otpRequest.getId(),
                audience,
                purpose,
                channel,
                accountState,
                otpRequest.getMaskedDestination(),
                accountState == AuthAccountState.NEW
                        ? "OTP sent successfully. We will create the account after verification."
                        : "OTP sent successfully",
                true,
                Math.max(0, MAX_OTP_ATTEMPTS - otpRequest.getAttempts()),
                otpRequest.getResendAvailableAt() == null ? 0 : secondsUntil(otpRequest.getResendAvailableAt()),
                otpRequest.getLockedUntil());
    }

    public AuthSessionData verifyOtp(AuthAudience audience, OtpVerifyPayload payload) {
        if (payload.otpRequestId() == null) {
            throw new ResponseStatusException(BAD_REQUEST, "otpRequestId is required");
        }

        if (payload.code() == null || !payload.code().trim().matches("\\d{6}")) {
            throw new ResponseStatusException(BAD_REQUEST, "OTP code must be a 6-digit number");
        }

        AbstractOtpRequest otpRequest = findOtpRequest(audience, payload.otpRequestId())
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "OTP request not found"));

        if (otpRequest.getStatus() == OtpRequestStatus.VERIFIED) {
            throw new ResponseStatusException(CONFLICT, "OTP already verified");
        }

        if (AuthSupport.isExpired(otpRequest.getExpiresAt())) {
            otpRequest.setStatus(OtpRequestStatus.EXPIRED);
            saveOtpRequest(audience, otpRequest);
            throw new ResponseStatusException(GONE, "OTP expired. Please request a new code.");
        }

        if (isLockActive(otpRequest, Instant.now())) {
            throw new ResponseStatusException(TOO_MANY_REQUESTS, lockMessage(otpRequest, Instant.now()));
        }

        if (otpRequest.getAttempts() >= MAX_OTP_ATTEMPTS) {
            otpRequest.setStatus(OtpRequestStatus.LOCKED);
            if (otpRequest.getLockedUntil() == null) {
                otpRequest.setLockedUntil(Instant.now().plus(OTP_LOCK_MINUTES, ChronoUnit.MINUTES));
            }
            saveOtpRequest(audience, otpRequest);
            throw new ResponseStatusException(TOO_MANY_REQUESTS, lockMessage(otpRequest, Instant.now()));
        }

        String expectedHash = AuthSupport.hashOtp(String.valueOf(otpRequest.getId()), payload.code().trim());
        if (!expectedHash.equals(otpRequest.getOtpHash())) {
            otpRequest.setAttempts(otpRequest.getAttempts() + 1);
            int attemptsLeft = Math.max(0, MAX_OTP_ATTEMPTS - otpRequest.getAttempts());
            if (otpRequest.getAttempts() >= MAX_OTP_ATTEMPTS) {
                otpRequest.setStatus(OtpRequestStatus.LOCKED);
                otpRequest.setLockedUntil(Instant.now().plus(OTP_LOCK_MINUTES, ChronoUnit.MINUTES));
            }
            saveOtpRequest(audience, otpRequest);
            if (otpRequest.getStatus() == OtpRequestStatus.LOCKED) {
                throw new ResponseStatusException(TOO_MANY_REQUESTS, lockMessage(otpRequest, Instant.now()));
            }
            throw new ResponseStatusException(UNAUTHORIZED, "Invalid OTP. " + attemptsLeft + " attempts left.");
        }

        otpRequest.setStatus(OtpRequestStatus.VERIFIED);
        otpRequest.setVerifiedAt(Instant.now());
        saveOtpRequest(audience, otpRequest);

        AbstractAuthUser user = resolveUserForVerification(audience, otpRequest);
        user.setStatus(AuthUserStatus.ACTIVE);
        if (otpRequest.getChannel() == AuthChannel.EMAIL) {
            user.setEmailVerifiedAt(Instant.now());
        } else {
            user.setPhoneVerifiedAt(Instant.now());
        }
        saveUser(audience, user);

        String accessToken = AuthSupport.generateToken();
        String refreshToken = AuthSupport.generateToken();
        Instant expiresAt = Instant.now().plus(SESSION_TTL_DAYS, ChronoUnit.DAYS);
        AbstractAuthSession session = createSession(audience, user.getId(), accessToken, refreshToken, expiresAt);
        saveSession(audience, session);

        Optional<com.ecommerce.store.StoreProfile> storeProfile = resolveStoreProfile(audience, user.getPublicId());

        return new AuthSessionData(
                user.getId(),
                user.getPublicId(),
                storeProfile.map(store -> store.getOrgId()).orElse(null),
                storeProfile.map(store -> store.getStoreId()).orElse(null),
                accessToken,
                refreshToken,
                expiresAt,
                audience,
                resolveState(user),
                audience == AuthAudience.ADMIN && storeProfile.isEmpty());
    }

    public OAuthStartData startOAuth(AuthAudience audience, String provider) {
        String providerName = normalizeProvider(provider);
        return new OAuthStartData(
                providerName,
                false,
                "",
                "OAuth provider is not configured yet. Use OTP for now.");
    }

    public OAuthCallbackData callbackOAuth(AuthAudience audience, String provider, OAuthCallbackPayload payload) {
        String providerName = normalizeProvider(provider);
        return new OAuthCallbackData(
                providerName,
                false,
                "OAuth callback is not configured yet.",
                "",
                "");
    }

    private AuthPurpose parsePurpose(String purpose) {
        try {
            return AuthPurpose.from(purpose);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(BAD_REQUEST, ex.getMessage());
        }
    }

    private AuthChannel parseChannel(String channel) {
        try {
            return AuthChannel.from(channel);
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(BAD_REQUEST, ex.getMessage());
        }
    }

    private String normalizeAndValidate(AuthChannel channel, String identifier) {
        if (!AuthSupport.isValidIdentifier(channel, identifier)) {
            throw new ResponseStatusException(BAD_REQUEST, "Invalid " + channel.name().toLowerCase() + " identifier");
        }
        return AuthSupport.normalizeIdentifier(channel, identifier);
    }

    private AuthAccountState resolveState(Optional<? extends AbstractAuthUser> user) {
        return user.map(this::resolveState).orElse(AuthAccountState.NEW);
    }

    private AuthAccountState resolveState(AbstractAuthUser user) {
        if (user.getStatus() == AuthUserStatus.PENDING) {
            return AuthAccountState.PENDING;
        }
        return AuthAccountState.EXISTING;
    }

    private Optional<? extends AbstractAuthUser> findUser(AuthAudience audience, AuthChannel channel, String normalizedIdentifier) {
        return switch (audience) {
            case ADMIN -> findAdminUser(channel, normalizedIdentifier);
            case CONSUMER -> findConsumerUser(channel, normalizedIdentifier);
        };
    }

    private Optional<AdminAuthUser> findAdminUser(AuthChannel channel, String normalizedIdentifier) {
        return channel == AuthChannel.EMAIL
                ? adminAuthUserRepository.findByEmailIgnoreCase(normalizedIdentifier)
                : adminAuthUserRepository.findByPhoneNumber(normalizedIdentifier);
    }

    private Optional<ConsumerAuthUser> findConsumerUser(AuthChannel channel, String normalizedIdentifier) {
        return channel == AuthChannel.EMAIL
                ? consumerAuthUserRepository.findByEmailIgnoreCase(normalizedIdentifier)
                : consumerAuthUserRepository.findByPhoneNumber(normalizedIdentifier);
    }

    private AbstractAuthUser createPendingUser(AuthAudience audience, AuthChannel channel, String normalizedIdentifier, String displayName) {
        AbstractAuthUser user = switch (audience) {
            case ADMIN -> new AdminAuthUser();
            case CONSUMER -> new ConsumerAuthUser();
        };

        applyIdentifier(user, channel, normalizedIdentifier);
        user.setDisplayName(displayName == null || displayName.isBlank() ? null : displayName.trim());
        user.setStatus(AuthUserStatus.PENDING);
        return saveUser(audience, user);
    }

    private void applyIdentifier(AbstractAuthUser user, AuthChannel channel, String normalizedIdentifier) {
        if (channel == AuthChannel.EMAIL) {
            user.setEmail(normalizedIdentifier);
        } else {
            user.setPhoneNumber(normalizedIdentifier);
        }
    }

    private AbstractOtpRequest createOtpRequest(
            AuthAudience audience,
            AuthPurpose purpose,
            AuthChannel channel,
            String identifier,
            String normalizedIdentifier,
            Long userId) {
        AbstractOtpRequest request = switch (audience) {
            case ADMIN -> new AdminOtpRequest();
            case CONSUMER -> new ConsumerOtpRequest();
        };

        request.setPurpose(purpose);
        request.setChannel(channel);
        request.setUserId(userId);
        request.setIdentifier(identifier);
        request.setNormalizedIdentifier(normalizedIdentifier);
        request.setMaskedDestination(AuthSupport.maskIdentifier(channel, normalizedIdentifier));
        request.setStatus(OtpRequestStatus.PENDING);
        request.setAttempts(0);
        request.setRequestCount(1);
        request.setExpiresAt(Instant.now().plus(OTP_TTL_MINUTES, ChronoUnit.MINUTES));
        request.setOtpHash("PENDING");
        saveOtpRequest(audience, request);
        return request;
    }

    private AbstractAuthUser resolveUserForVerification(AuthAudience audience, AbstractOtpRequest otpRequest) {
        if (otpRequest.getUserId() != null) {
            return findUserById(audience, otpRequest.getUserId())
                    .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Linked account not found"));
        }

        AbstractAuthUser user = switch (audience) {
            case ADMIN -> new AdminAuthUser();
            case CONSUMER -> new ConsumerAuthUser();
        };
        applyIdentifier(user, otpRequest.getChannel(), otpRequest.getNormalizedIdentifier());
        user.setDisplayName(null);
        user.setStatus(AuthUserStatus.ACTIVE);
        return saveUser(audience, user);
    }

    private Optional<? extends AbstractAuthUser> findUserById(AuthAudience audience, Long userId) {
        return switch (audience) {
            case ADMIN -> adminAuthUserRepository.findById(userId).map(user -> (AbstractAuthUser) user);
            case CONSUMER -> consumerAuthUserRepository.findById(userId).map(user -> (AbstractAuthUser) user);
        };
    }

    private AbstractAuthUser saveUser(AuthAudience audience, AbstractAuthUser user) {
        return switch (audience) {
            case ADMIN -> adminAuthUserRepository.save((AdminAuthUser) user);
            case CONSUMER -> consumerAuthUserRepository.save((ConsumerAuthUser) user);
        };
    }

    private Optional<? extends AbstractOtpRequest> findOtpRequest(AuthAudience audience, Long requestId) {
        return switch (audience) {
            case ADMIN -> adminOtpRequestRepository.findById(requestId).map(request -> (AbstractOtpRequest) request);
            case CONSUMER -> consumerOtpRequestRepository.findById(requestId).map(request -> (AbstractOtpRequest) request);
        };
    }

    private AbstractOtpRequest saveOtpRequest(AuthAudience audience, AbstractOtpRequest request) {
        return switch (audience) {
            case ADMIN -> adminOtpRequestRepository.save((AdminOtpRequest) request);
            case CONSUMER -> consumerOtpRequestRepository.save((ConsumerOtpRequest) request);
        };
    }

    private AbstractAuthSession createSession(
            AuthAudience audience,
            Long userId,
            String accessToken,
            String refreshToken,
            Instant expiresAt) {
        AbstractAuthSession session = switch (audience) {
            case ADMIN -> new AdminAuthSession();
            case CONSUMER -> new ConsumerAuthSession();
        };
        session.setUserId(userId);
        session.setAccessTokenHash(AuthSupport.hashToken(accessToken));
        session.setRefreshTokenHash(AuthSupport.hashToken(refreshToken));
        session.setExpiresAt(expiresAt);
        return session;
    }

    private AbstractAuthSession saveSession(AuthAudience audience, AbstractAuthSession session) {
        return switch (audience) {
            case ADMIN -> adminAuthSessionRepository.save((AdminAuthSession) session);
            case CONSUMER -> consumerAuthSessionRepository.save((ConsumerAuthSession) session);
        };
    }

    private void deliverOtp(AuthChannel channel, String normalizedIdentifier, String otpCode) {
        otpDeliveryService.deliver(channel, normalizedIdentifier, otpCode);
    }

    private Optional<? extends AbstractOtpRequest> resolveLatestOtpRequest(
            AuthAudience audience,
            AuthChannel channel,
            String normalizedIdentifier,
            AuthPurpose purpose) {
        return switch (audience) {
            case ADMIN -> adminOtpRequestRepository
                    .findTopByNormalizedIdentifierAndPurposeAndChannelOrderByCreatedAtDesc(
                            normalizedIdentifier,
                            purpose,
                            channel)
                    .map(request -> (AbstractOtpRequest) request);
            case CONSUMER -> consumerOtpRequestRepository
                    .findTopByNormalizedIdentifierAndPurposeAndChannelOrderByCreatedAtDesc(
                            normalizedIdentifier,
                            purpose,
                            channel)
                    .map(request -> (AbstractOtpRequest) request);
        };
    }

    private boolean isLockActive(AbstractOtpRequest request, Instant now) {
        return request.getLockedUntil() != null && now.isBefore(request.getLockedUntil());
    }

    private boolean isResendCoolingDown(AbstractOtpRequest request, Instant now) {
        return request.getResendAvailableAt() != null && now.isBefore(request.getResendAvailableAt());
    }

    private int secondsUntil(Instant instant) {
        if (instant == null) {
            return 0;
        }
        long seconds = Instant.now().until(instant, ChronoUnit.SECONDS);
        return (int) Math.max(0, seconds);
    }

    private String resendMessage(AbstractOtpRequest request, Instant now) {
        int seconds = secondsUntil(request.getResendAvailableAt());
        if (seconds > 0) {
            return "Please wait " + seconds + " seconds before requesting another OTP.";
        }
        return "Please wait before requesting another OTP.";
    }

    private String lockMessage(AbstractOtpRequest request, Instant now) {
        int seconds = secondsUntil(request.getLockedUntil());
        if (seconds > 0) {
            int minutes = Math.max(1, (int) Math.ceil(seconds / 60.0));
            return "This email or phone is temporarily locked for " + minutes + " minutes. Please try again later.";
        }
        return "This email or phone is temporarily locked. Please try again later.";
    }

    private Optional<com.ecommerce.store.StoreProfile> resolveStoreProfile(AuthAudience audience, String publicUserId) {
        if (publicUserId == null || publicUserId.isBlank()) {
            return Optional.empty();
        }
        return storeProfileRepository.findByOwnerPublicUserId(publicUserId.trim())
                .filter(profile -> profile.getAudience() == audience);
    }

    private String normalizeProvider(String provider) {
        if (provider == null || provider.isBlank()) {
            return "unknown";
        }
        return provider.trim().toLowerCase();
    }
}
