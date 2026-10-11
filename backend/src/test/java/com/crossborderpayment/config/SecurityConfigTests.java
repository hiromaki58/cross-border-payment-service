package com.crossborderpayment.config;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Every endpoint requires authentication unless it is explicitly made public,
 * and state-changing requests require a CSRF token.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SecurityConfigTests {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void unauthenticatedRequestReturns401() throws Exception {
		mockMvc.perform(get("/api/remitter/anything"))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void unauthenticatedPostWithCsrfTokenReturns401() throws Exception {
		mockMvc.perform(post("/api/remitter/anything").with(csrf()))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void httpBasicCredentialsAreNotAccepted() throws Exception {
		mockMvc.perform(get("/api/remitter/anything").header("Authorization", "Basic dXNlcjp1c2Vy"))
			.andExpect(status().isUnauthorized());
	}

	@Test
	@WithMockUser
	void authenticatedRequestPassesSecurity() throws Exception {
		// No endpoint exists yet, so a request that passes security reaches MVC and gets 404
		mockMvc.perform(get("/api/remitter/anything"))
			.andExpect(status().isNotFound());
	}

	@Test
	@WithMockUser
	void authenticatedPostWithoutCsrfTokenIsRejected() throws Exception {
		mockMvc.perform(post("/api/remitter/anything"))
			.andExpect(status().isForbidden());
	}

}
