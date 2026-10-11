package com.crossborderpayment.service;

import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.crossborderpayment.config.AuthProperties;

/**
 * Sends authentication-related e-mails (plain text, fixed wording).
 *
 * Sending is asynchronous so that the response time of the public endpoints does not reveal
 * whether an e-mail was sent (i.e. whether an address is registered).
 * Codes and e-mail addresses are never written to the log.
 */
@Service
public class AuthMailSender {

    private static final Logger log = LoggerFactory.getLogger(AuthMailSender.class);

    private final ObjectProvider<JavaMailSender> mailSender;
    private final AuthProperties properties;

    public AuthMailSender(ObjectProvider<JavaMailSender> mailSender, AuthProperties properties) {
        this.mailSender = mailSender;
        this.properties = properties;
    }

    @Async
    public void sendVerificationCode(String to, String code, Duration validity) {
        send("verification-code", to, "Your verification code",
                "Your verification code is: " + code + "\n\n"
                        + "The code expires in " + validity.toMinutes() + " minutes and can be used only once.\n"
                        + "Never share this code with anyone. We will never ask you for it.\n"
                        + "If you did not request this code, you can ignore this e-mail.");
    }

    @Async
    public void sendAlreadyRegisteredNotice(String to) {
        send("already-registered", to, "Registration attempt for your account",
                "Someone tried to register a new account with this e-mail address, but an account already exists.\n\n"
                        + "If this was you and you can no longer log in, use account recovery on the login page.\n"
                        + "If this was not you, you can ignore this e-mail.");
    }

    @Async
    public void sendPasskeyAddedNotice(String to) {
        send("passkey-added", to, "A new passkey was added to your account",
                "A new passkey was added to your account.\n\n"
                        + "If you did not do this, recover your account immediately and remove unknown passkeys.");
    }

    @Async
    public void sendPasskeyRemovedNotice(String to) {
        send("passkey-removed", to, "A passkey was removed from your account",
                "A passkey was removed from your account.\n\n"
                        + "If you did not do this, recover your account immediately.");
    }

    private void send(String type, String to, String subject, String text) {
        JavaMailSender sender = mailSender.getIfAvailable();
        if (sender == null) {
            log.warn("Mail is not configured (SPRING_MAIL_HOST); {} mail was not sent", type);
            return;
        }
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(properties.mail().from());
        message.setTo(to);
        message.setSubject(subject);
        message.setText(text);
        try {
            sender.send(message);
        } catch (MailException ex) {
            // The exception message may contain the address, so only the type is logged
            log.warn("Failed to send {} mail: {}", type, ex.getClass().getSimpleName());
        }
    }
}
