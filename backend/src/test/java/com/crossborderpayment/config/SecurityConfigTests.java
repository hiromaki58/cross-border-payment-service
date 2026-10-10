package com.crossborderpayment.config;

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
 * Until endpoints are implemented, the baseline configuration must deny every request.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SecurityConfigTests {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void unauthenticatedRequestIsDenied() throws Exception {
		mockMvc.perform(get("/api/anything"))
			.andExpect(status().isForbidden());
	}

	@Test
	@WithMockUser
	void authenticatedRequestIsAlsoDenied() throws Exception {
		mockMvc.perform(get("/api/anything"))
			.andExpect(status().isForbidden());
	}

	@Test
	void unauthenticatedPostIsDenied() throws Exception {
		mockMvc.perform(post("/api/player/login"))
			.andExpect(status().isForbidden());
	}

	@Test
	void httpBasicCredentialsAreNotAccepted() throws Exception {
		mockMvc.perform(get("/api/anything").header("Authorization", "Basic dXNlcjp1c2Vy"))
			.andExpect(status().isForbidden());
	}

}
