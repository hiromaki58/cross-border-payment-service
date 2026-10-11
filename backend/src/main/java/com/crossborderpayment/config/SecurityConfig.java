package com.crossborderpayment.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;

/**
 * Security configuration.
 *
 * Every request requires authentication unless it is explicitly listed as public.
 * Public endpoints (e.g. Google login, one-time e-mail code login) are added with
 * requestMatchers(...).permitAll() when they are implemented.
 * CSRF protection stays enabled (Spring Security default).
 */
@Configuration
public class SecurityConfig {

	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
		http
			.authorizeHttpRequests(auth -> auth.anyRequest().authenticated())
			// Not logged in -> 401, logged in but not allowed -> 403
			.exceptionHandling(ex -> ex.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
			.httpBasic(AbstractHttpConfigurer::disable)
			.formLogin(AbstractHttpConfigurer::disable);
		return http.build();
	}

}
