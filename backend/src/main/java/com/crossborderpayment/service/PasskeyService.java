package com.crossborderpayment.service;

import java.util.Comparator;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcOperations;
import org.springframework.security.web.webauthn.api.Bytes;
import org.springframework.security.web.webauthn.api.CredentialRecord;
import org.springframework.security.web.webauthn.api.PublicKeyCredentialUserEntity;
import org.springframework.security.web.webauthn.management.PublicKeyCredentialUserEntityRepository;
import org.springframework.security.web.webauthn.management.UserCredentialRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.crossborderpayment.dto.PasskeyResponse;

/**
 * Passkey management for the logged-in remitter.
 *
 * The owner is always the authenticated user determined on the server (username from the
 * security context); a credential id supplied by the client is only used after checking that
 * it belongs to that user.
 */
@Service
public class PasskeyService {

    public enum DeleteResult { DELETED, NOT_FOUND, LAST_PASSKEY }

    private static final Logger log = LoggerFactory.getLogger(PasskeyService.class);

    private final PublicKeyCredentialUserEntityRepository userEntities;
    private final UserCredentialRepository userCredentials;
    private final JdbcOperations jdbc;
    private final AuthMailSender mailSender;

    public PasskeyService(PublicKeyCredentialUserEntityRepository userEntities,
            UserCredentialRepository userCredentials, JdbcOperations jdbc, AuthMailSender mailSender) {
        this.userEntities = userEntities;
        this.userCredentials = userCredentials;
        this.jdbc = jdbc;
        this.mailSender = mailSender;
    }

    @Transactional(readOnly = true)
    public List<PasskeyResponse> list(String username) {
        PublicKeyCredentialUserEntity owner = userEntities.findByUsername(username);
        if (owner == null) {
            return List.of();
        }
        return userCredentials.findByUserId(owner.getId()).stream()
                .sorted(Comparator.comparing(CredentialRecord::getCreated))
                .map(PasskeyResponse::from)
                .toList();
    }

    @Transactional
    public DeleteResult delete(String username, String credentialId) {
        PublicKeyCredentialUserEntity owner = userEntities.findByUsername(username);
        if (owner == null) {
            return DeleteResult.NOT_FOUND;
        }
        // Lock the owner's row so that concurrent deletions cannot remove the last passkey
        jdbc.queryForList("SELECT id FROM user_entities WHERE id = ? FOR UPDATE", String.class,
                owner.getId().toBase64UrlString());

        Bytes id;
        try {
            id = Bytes.fromBase64(credentialId);
        } catch (IllegalArgumentException ex) {
            return DeleteResult.NOT_FOUND;
        }
        CredentialRecord credential = userCredentials.findByCredentialId(id);
        // Another user's passkey is reported exactly like a missing one (no information about other users)
        if (credential == null || !owner.getId().equals(credential.getUserEntityUserId())) {
            log.warn("Passkey deletion rejected: not found or not owned");
            return DeleteResult.NOT_FOUND;
        }
        if (userCredentials.findByUserId(owner.getId()).size() <= 1) {
            return DeleteResult.LAST_PASSKEY;
        }
        userCredentials.delete(id);
        log.info("Passkey deleted: userHandle={}", owner.getId().toBase64UrlString());
        mailSender.sendPasskeyRemovedNotice(username);
        return DeleteResult.DELETED;
    }
}
