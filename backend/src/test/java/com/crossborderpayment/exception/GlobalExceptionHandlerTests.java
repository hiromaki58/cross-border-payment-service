package com.crossborderpayment.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/**
 * Error responses must use the right status code and must not expose internal details
 * (SQL, constraint names, exception messages, submitted values).
 */
class GlobalExceptionHandlerTests {

    record TestRequest(@NotBlank @Email String emailAddr) {
    }

    @RestController
    static class TestController {
        @PostMapping("/test/validate")
        String validate(@Valid @RequestBody TestRequest request) {
            return "ok";
        }

        @GetMapping("/test/conflict")
        String conflict() {
            throw new DataIntegrityViolationException(
                    "duplicate key value violates unique constraint \"uk_remitter_model_email_addr\" Key (email_addr)=(taken@example.com)");
        }

        @GetMapping("/test/unexpected")
        String unexpected() {
            throw new IllegalStateException("internal detail: jdbc:postgresql://internal-db:5432");
        }
    }

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new TestController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void validRequestSucceeds() throws Exception {
        mockMvc.perform(post("/test/validate").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"emailAddr\":\"user@example.com\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void invalidInputReturns400WithoutSubmittedValue() throws Exception {
        MvcResult result = mockMvc.perform(post("/test/validate").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"emailAddr\":\"not-an-email-<script>\"}"))
                .andExpect(status().isBadRequest())
                .andReturn();

        assertThat(result.getResponse().getContentAsString()).doesNotContain("not-an-email", "<script>");
    }

    @Test
    void missingRequiredInputReturns400() throws Exception {
        mockMvc.perform(post("/test/validate").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void malformedJsonReturns400() throws Exception {
        MvcResult result = mockMvc.perform(post("/test/validate").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"emailAddr\":"))
                .andExpect(status().isBadRequest())
                .andReturn();

        assertThat(result.getResponse().getContentAsString()).doesNotContainIgnoringCase("jackson");
    }

    @Test
    void dataIntegrityViolationReturns409WithoutInternalDetails() throws Exception {
        MvcResult result = mockMvc.perform(get("/test/conflict"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("The request conflicts with existing data."))
                .andReturn();

        assertThat(result.getResponse().getContentAsString())
                .doesNotContain("uk_remitter_model_email_addr", "taken@example.com", "duplicate key", "Exception");
    }

    @Test
    void unexpectedExceptionReturns500WithoutInternalDetails() throws Exception {
        MvcResult result = mockMvc.perform(get("/test/unexpected"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.detail").value("An unexpected error occurred."))
                .andReturn();

        assertThat(result.getResponse().getContentAsString())
                .doesNotContain("jdbc", "internal-db", "IllegalStateException");
    }
}
