package com.crossborderpayment.service;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.crossborderpayment.config.AuthProperties;
import com.crossborderpayment.model.EmailAddressNormalizer;
import com.crossborderpayment.model.EmailVerificationChallengeModel;
import com.crossborderpayment.model.EmailVerificationPurpose;
import com.crossborderpayment.model.RemitterModel;
import com.crossborderpayment.repository.EmailVerificationChallengeRepository;
import com.crossborderpayment.repository.RemitterRepository;

/**
 * E-mail address verification with a one-time code, used before a passkey is registered
 * (new registration or account recovery).
 *
 * The public endpoints behave the same whether or not an address is registered (no account
 * enumeration): a challenge is always created and only the e-mail content differs.
 * Codes expire, can be used only once, allow a limited number of attempts, and only a limited
 * number of codes can be requested per address and time window (also limits e-mail bombing).
 */
@Service
public class EmailVerificationService {

    private static final Logger log = LoggerFactory.getLogger(EmailVerificationService.class);

    // Challenge rows (which contain an e-mail address) are kept only as long as needed for rate limiting
    private static final Duration RETENTION = Duration.ofDays(1);

    public record VerifiedEmail(String emailAddr, EmailVerificationPurpose purpose) {
    }

    private final EmailVerificationChallengeRepository challengeRepository;
    private final RemitterRepository remitterRepository;
    private final OneTimeCodeService oneTimeCodeService;
    private final AuthMailSender mailSender;
    private final AuthProperties.EmailVerification settings;

    public EmailVerificationService(EmailVerificationChallengeRepository challengeRepository,
            RemitterRepository remitterRepository, OneTimeCodeService oneTimeCodeService, AuthMailSender mailSender,
            AuthProperties properties) {
        this.challengeRepository = challengeRepository;
        this.remitterRepository = remitterRepository;
        this.oneTimeCodeService = oneTimeCodeService;
        this.mailSender = mailSender;
        this.settings = properties.emailVerification();
    }

    /**
     * @return the challenge id to bind to the caller's session, or empty when the request was rate limited
     */
    @Transactional
    public Optional<Long> requestRegistration(String emailAddr, String firstName, String familyName) {
        String normalized = EmailAddressNormalizer.normalize(emailAddr);
        if (isRateLimited(normalized)) {
            return Optional.empty();
        }
        if (remitterRepository.existsByEmail(normalized)) {
            // Do not reveal on the API that the address exists; tell the owner by e-mail instead
            EmailVerificationChallengeModel challenge = save(EmailVerificationPurpose.REGISTRATION, normalized,
                    null, null, null);
            mailSender.sendAlreadyRegisteredNotice(normalized);
            return Optional.of(challenge.getId());
        }
        return Optional.of(issueCode(EmailVerificationPurpose.REGISTRATION, normalized, firstName.trim(),
                familyName.trim()));
    }

    /**
     * @return the challenge id to bind to the caller's session, or empty when the request was rate limited
     */
    @Transactional
    public Optional<Long> requestRecovery(String emailAddr) {
        String normalized = EmailAddressNormalizer.normalize(emailAddr);
        if (isRateLimited(normalized)) {
            return Optional.empty();
        }
        if (!remitterRepository.existsByEmail(normalized)) {
            // No e-mail for unknown addresses; the stored challenge (without a code) can never be verified
            return Optional.of(save(EmailVerificationPurpose.RECOVERY, normalized, null, null, null).getId());
        }
        return Optional.of(issueCode(EmailVerificationPurpose.RECOVERY, normalized, null, null));
    }

    /**
     * Checks a code against the challenge bound to the caller's session.
     * For a registration, the remitter is created once the address is verified.
     */
    @Transactional
    public Optional<VerifiedEmail> verify(Long challengeId, String code) {
        if (challengeId == null) {
            return Optional.empty();
        }
        EmailVerificationChallengeModel challenge = challengeRepository.findByIdForUpdate(challengeId).orElse(null);
        Instant now = Instant.now();
        if (challenge == null || !challenge.isUsable(now, settings.maxAttempts())) {
            log.info("Email verification rejected: challengeId={}, reason=unusable", challengeId);
            return Optional.empty();
        }
        challenge.recordAttempt();
        if (!oneTimeCodeService.matches(challenge.getCodeHash(), challenge.getPurpose().name(),
                challenge.getEmailAddr(), code)) {
            log.info("Email verification rejected: challengeId={}, reason=wrong-code, attempts={}", challengeId,
                    challenge.getAttemptCount());
            return Optional.empty();
        }
        challenge.markConsumed(now);

        if (challenge.getPurpose() == EmailVerificationPurpose.REGISTRATION) {
            if (remitterRepository.existsByEmail(challenge.getEmailAddr())) {
                log.info("Email verification rejected: challengeId={}, reason=already-registered", challengeId);
                return Optional.empty();
            }
            remitterRepository.save(new RemitterModel(null, challenge.getFirstName(), challenge.getFamilyName(),
                    challenge.getEmailAddr()));
        }
        log.info("Email verification succeeded: challengeId={}, purpose={}", challengeId, challenge.getPurpose());
        return Optional.of(new VerifiedEmail(challenge.getEmailAddr(), challenge.getPurpose()));
    }

    @Scheduled(cron = "0 0 * * * *")
    @Transactional
    public void purgeOldChallenges() {
        int deleted = challengeRepository.deleteCreatedBefore(Instant.now().minus(RETENTION));
        if (deleted > 0) {
            log.info("Purged {} old email verification challenges", deleted);
        }
    }

    private boolean isRateLimited(String normalizedEmailAddr) {
        Instant windowStart = Instant.now().minus(settings.requestWindow());
        boolean limited = challengeRepository.countByEmailAddrAndCreatedAtAfter(normalizedEmailAddr, windowStart)
                >= settings.maxRequestsPerWindow();
        if (limited) {
            log.warn("Email verification request rate limited");
        }
        return limited;
    }

    private Long issueCode(EmailVerificationPurpose purpose, String normalizedEmailAddr, String firstName,
            String familyName) {
        String code = oneTimeCodeService.generateCode();
        String codeHash = oneTimeCodeService.hash(purpose.name(), normalizedEmailAddr, code);
        EmailVerificationChallengeModel challenge = save(purpose, normalizedEmailAddr, firstName, familyName, codeHash);
        mailSender.sendVerificationCode(normalizedEmailAddr, code, settings.codeTtl());
        log.info("Email verification code issued: challengeId={}, purpose={}", challenge.getId(), purpose);
        return challenge.getId();
    }

    private EmailVerificationChallengeModel save(EmailVerificationPurpose purpose, String normalizedEmailAddr,
            String firstName, String familyName, String codeHash) {
        Instant now = Instant.now();
        return challengeRepository.save(new EmailVerificationChallengeModel(purpose, normalizedEmailAddr, firstName,
                familyName, codeHash, now, now.plus(settings.codeTtl())));
    }
}
