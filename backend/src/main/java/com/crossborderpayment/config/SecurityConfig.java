package com.crossborderpayment.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationEventPublisher;
import org.springframework.security.authentication.DefaultAuthenticationEventPublisher;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.logout.HeaderWriterLogoutHandler;
import org.springframework.security.web.authentication.logout.HttpStatusReturningLogoutSuccessHandler;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.header.writers.ClearSiteDataHeaderWriter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.savedrequest.NullRequestCache;
import org.springframework.security.web.webauthn.management.UserCredentialRepository;
import org.springframework.security.web.webauthn.management.WebAuthnRelyingPartyOperations;

import com.crossborderpayment.repository.RemitterRepository;
import com.crossborderpayment.security.AuthSessionManager;
import com.crossborderpayment.security.PasskeyEndpoints;
import com.crossborderpayment.security.PasskeyRegistrationGuardFilter;
import com.crossborderpayment.security.RemitterUserDetailsService;
import com.crossborderpayment.security.Roles;
import com.crossborderpayment.security.SessionLifetimeFilter;
import com.crossborderpayment.service.AuthMailSender;

import jakarta.servlet.DispatcherType;

/**
 * Security configuration.
 *
 * Every request requires a passkey login (ROLE_REMITTER) unless it is explicitly listed below.
 *
 * Login flow:
 *  1. New user / account recovery: e-mail one-time code (/api/auth/registration, /api/auth/recovery,
 *     /api/auth/email-verification) -> limited session (ROLE_PASSKEY_ENROLLMENT) that can only
 *     register a passkey (/api/auth/passkey/register/options, /api/auth/passkey/register).
 *  2. Login: passkey with user verification (/api/auth/passkey/login/options,
 *     /api/auth/passkey/login) -> ROLE_REMITTER.
 * All endpoints are under /api (see PasskeyEndpoints).
 *
 * Sessions are server-side (HttpOnly / Secure / SameSite=Strict cookie). CSRF protection stays enabled,
 * using a readable XSRF-TOKEN cookie that the SPA sends back in the X-XSRF-TOKEN header.
 */
@Configuration
@EnableConfigurationProperties(AuthProperties.class)
public class SecurityConfig {

	private static final String SESSION_COOKIE_PROPERTY = "server.servlet.session.cookie.name";

	@Bean
	public SecurityFilterChain securityFilterChain(HttpSecurity http, RemitterRepository remitterRepository,
			AuthSessionManager authSessionManager, AuthMailSender authMailSender,
			SessionAuthenticationStrategy sessionAuthenticationStrategy,
			SecurityContextRepository securityContextRepository, CookieCsrfTokenRepository csrfTokenRepository,
			WebAuthnRelyingPartyOperations rpOperations, UserCredentialRepository userCredentials,
			AuthenticationEventPublisher authenticationEventPublisher, Environment environment) throws Exception {
		// Used only by the passkey provider (not a bean, so no password-based provider is created)
		UserDetailsService userDetailsService = new RemitterUserDetailsService(remitterRepository);

		http
			.authorizeHttpRequests(auth -> auth
				// Error responses (e.g. 403 for a missing CSRF token) are rendered by an internal ERROR dispatch to
				// /error; it must not be re-authorized, otherwise every error becomes 401 for anonymous users.
				// The error body never contains details (server.error.include-* = never).
				.dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
				.requestMatchers(HttpMethod.GET, "/api/auth/csrf").permitAll()
				.requestMatchers(HttpMethod.POST, "/api/auth/registration", "/api/auth/recovery",
						"/api/auth/email-verification").permitAll()
				.requestMatchers(HttpMethod.POST, PasskeyEndpoints.LOGIN_OPTIONS, PasskeyEndpoints.LOGIN).permitAll()
				// Further checked by PasskeyRegistrationGuardFilter (role, recent login)
				.requestMatchers(HttpMethod.POST, PasskeyEndpoints.REGISTER_OPTIONS, PasskeyEndpoints.REGISTER)
					.hasAnyRole(Roles.REMITTER, Roles.PASSKEY_ENROLLMENT)
				.requestMatchers(HttpMethod.GET, "/api/auth/session").hasAnyRole(Roles.REMITTER, Roles.PASSKEY_ENROLLMENT)
				.anyRequest().hasRole(Roles.REMITTER))
			// Not logged in -> 401, logged in but not allowed -> 403
			.exceptionHandling(ex -> ex.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
			.httpBasic(AbstractHttpConfigurer::disable)
			.formLogin(AbstractHttpConfigurer::disable)
			.requestCache(cache -> cache.requestCache(new NullRequestCache()))
			.securityContext(context -> context.securityContextRepository(securityContextRepository))
			.csrf(csrf -> csrf.spa().csrfTokenRepository(csrfTokenRepository))
			.sessionManagement(session -> session
				.sessionFixation(fixation -> fixation.changeSessionId()))
			.logout(logout -> logout
				.logoutUrl("/api/auth/logout")
				.logoutSuccessHandler(new HttpStatusReturningLogoutSuccessHandler(HttpStatus.NO_CONTENT))
				.invalidateHttpSession(true)
				.clearAuthentication(true)
				.deleteCookies(environment.getProperty(SESSION_COOKIE_PROPERTY, "JSESSIONID"))
				.addLogoutHandler(new HeaderWriterLogoutHandler(
						new ClearSiteDataHeaderWriter(ClearSiteDataHeaderWriter.Directive.COOKIES,
								ClearSiteDataHeaderWriter.Directive.STORAGE))))
			.headers(headers -> headers
				// The API only returns JSON: nothing may be loaded, framed or executed from its responses
				.contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'none'; frame-ancestors 'none'"))
				.referrerPolicy(referrer -> referrer.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER)))
			.addFilterAfter(new SessionLifetimeFilter(authSessionManager), SecurityContextHolderFilter.class)
			.addFilterBefore(new PasskeyRegistrationGuardFilter(authSessionManager, authMailSender), CsrfFilter.class)
			// Passkey ceremonies (same positions as Spring Security's own WebAuthn configuration)
			.addFilterBefore(PasskeyEndpoints.loginFilter(rpOperations, userDetailsService, authenticationEventPublisher,
					sessionAuthenticationStrategy, securityContextRepository, authSessionManager),
					BasicAuthenticationFilter.class)
			.addFilterBefore(PasskeyEndpoints.loginOptionsFilter(rpOperations), AuthorizationFilter.class)
			.addFilterBefore(PasskeyEndpoints.registerOptionsFilter(rpOperations), AuthorizationFilter.class)
			.addFilterAfter(PasskeyEndpoints.registerFilter(userCredentials, rpOperations), AuthorizationFilter.class);
		return http.build();
	}

	// Publishes login success / failure events for the audit log
	@Bean
	public AuthenticationEventPublisher authenticationEventPublisher(ApplicationEventPublisher publisher) {
		return new DefaultAuthenticationEventPublisher(publisher);
	}

}
