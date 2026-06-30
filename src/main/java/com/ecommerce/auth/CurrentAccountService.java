package com.ecommerce.auth;

import com.ecommerce.auth.entity.AbstractAuthSession;
import com.ecommerce.auth.entity.AbstractAuthUser;
import com.ecommerce.auth.entity.AdminAuthSession;
import com.ecommerce.auth.entity.AdminAuthUser;
import com.ecommerce.auth.entity.ConsumerAuthSession;
import com.ecommerce.auth.entity.ConsumerAuthUser;
import com.ecommerce.auth.repository.AdminAuthSessionRepository;
import com.ecommerce.auth.repository.AdminAuthUserRepository;
import com.ecommerce.auth.repository.ConsumerAuthSessionRepository;
import com.ecommerce.auth.repository.ConsumerAuthUserRepository;
import com.ecommerce.store.BusinessStoreService;
import java.time.Instant;
import java.util.Optional;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@Transactional(readOnly = true)
public class CurrentAccountService {
    private final AdminAuthSessionRepository adminAuthSessionRepository;
    private final ConsumerAuthSessionRepository consumerAuthSessionRepository;
    private final AdminAuthUserRepository adminAuthUserRepository;
    private final ConsumerAuthUserRepository consumerAuthUserRepository;
    private final BusinessStoreService businessStoreService;

    public CurrentAccountService(
            AdminAuthSessionRepository adminAuthSessionRepository,
            ConsumerAuthSessionRepository consumerAuthSessionRepository,
            AdminAuthUserRepository adminAuthUserRepository,
            ConsumerAuthUserRepository consumerAuthUserRepository,
            BusinessStoreService businessStoreService) {
        this.adminAuthSessionRepository = adminAuthSessionRepository;
        this.consumerAuthSessionRepository = consumerAuthSessionRepository;
        this.adminAuthUserRepository = adminAuthUserRepository;
        this.consumerAuthUserRepository = consumerAuthUserRepository;
        this.businessStoreService = businessStoreService;
    }

    public CurrentAccountData resolveCurrentAccount(String authorizationHeader) {
        String token = extractBearerToken(authorizationHeader);
        String tokenHash = AuthSupport.hashToken(token);
        SessionMatch sessionMatch = findSession(tokenHash)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid or expired access token"));

        AbstractAuthSession session = sessionMatch.session();
        if (session.getRevokedAt() != null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Access token has been revoked");
        }

        if (session.getExpiresAt() != null && Instant.now().isAfter(session.getExpiresAt())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Access token has expired");
        }

        AbstractAuthUser user = findUser(sessionMatch.audience(), session.getUserId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Linked account not found"));

        Optional<BusinessStoreData> store = businessStoreService.findStoreProfile(sessionMatch.audience(), user.getPublicId());
        return new CurrentAccountData(
                sessionMatch.audience(),
                new CurrentAccountUserData(
                        user.getId(),
                        user.getPublicId(),
                        user.getDisplayName(),
                        user.getEmail(),
                        user.getPhoneNumber(),
                        user.getStatus().name(),
                        user.getEmailVerifiedAt(),
                        user.getPhoneVerifiedAt()),
                store.orElse(null),
                sessionMatch.audience() == AuthAudience.ADMIN && store.isEmpty());
    }

    private Optional<SessionMatch> findSession(String tokenHash) {
        Optional<SessionMatch> adminSession = adminAuthSessionRepository
                .findTopByAccessTokenHashAndRevokedAtIsNullOrderByUpdatedAtDesc(tokenHash)
                .map(session -> new SessionMatch(AuthAudience.ADMIN, session));
        if (adminSession.isPresent()) {
            return adminSession;
        }

        return consumerAuthSessionRepository
                .findTopByAccessTokenHashAndRevokedAtIsNullOrderByUpdatedAtDesc(tokenHash)
                .map(session -> new SessionMatch(AuthAudience.CONSUMER, session));
    }

    private Optional<? extends AbstractAuthUser> findUser(AuthAudience audience, Long userId) {
        return switch (audience) {
            case ADMIN -> adminAuthUserRepository.findById(userId).map(user -> (AbstractAuthUser) user);
            case CONSUMER -> consumerAuthUserRepository.findById(userId).map(user -> (AbstractAuthUser) user);
        };
    }

    private String extractBearerToken(String authorizationHeader) {
        if (authorizationHeader == null || authorizationHeader.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Authorization header is required");
        }

        String value = authorizationHeader.trim();
        if (!value.toLowerCase().startsWith("bearer ")) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Bearer token is required");
        }

        String token = value.substring(7).trim();
        if (token.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Bearer token is required");
        }

        return token;
    }

    private record SessionMatch(AuthAudience audience, AbstractAuthSession session) {}
}
