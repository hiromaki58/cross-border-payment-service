package com.crossborderpayment.security;

import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import com.crossborderpayment.repository.RemitterRepository;

/**
 * Loads the remitter after a passkey assertion has been verified (used only by the WebAuthn provider).
 *
 * There is no password login: the password is an unusable placeholder and no password-based
 * authentication provider is configured. This is deliberately not a bean, so Spring Security does
 * not create a global username/password provider from it.
 */
public class RemitterUserDetailsService implements UserDetailsService {

    // Not a valid encoded password for any PasswordEncoder, so it can never match
    private static final String NO_PASSWORD = "{none}";

    private final RemitterRepository remitterRepository;

    public RemitterUserDetailsService(RemitterRepository remitterRepository) {
        this.remitterRepository = remitterRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return remitterRepository.findByEmail(username)
                .map(remitter -> User.withUsername(remitter.getEmailAddr())
                        .password(NO_PASSWORD)
                        .roles(Roles.REMITTER)
                        .build())
                .orElseThrow(() -> new UsernameNotFoundException("Remitter not found"));
    }
}
