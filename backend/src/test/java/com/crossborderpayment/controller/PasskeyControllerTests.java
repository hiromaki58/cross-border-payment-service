package com.crossborderpayment.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;

import com.crossborderpayment.security.ProblemResponses;
import com.crossborderpayment.support.AuthIntegrationTestSupport;
import com.crossborderpayment.support.PasskeyTestClient;

/**
 * /api/passkeys: a remitter can only see and remove their own passkeys (IDOR / BOLA).
 */
class PasskeyControllerTests extends AuthIntegrationTestSupport {

    private static final String ALICE = "alice@example.com";
    private static final String BOB = "bob@example.com";

    private List<String> credentialIdsOf(String emailAddr) {
        return jdbc.queryForList("SELECT c.credential_id FROM user_credentials c "
                + "JOIN user_entities u ON u.id = c.user_entity_user_id WHERE u.name = ? ORDER BY c.created",
                String.class, emailAddr);
    }

    // --- unauthenticated ---

    @Test
    void unauthenticatedRequestsAreRejected() throws Exception {
        mockMvc.perform(get("/api/passkeys")).andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/passkeys/AAAA").with(csrf())).andExpect(status().isUnauthorized());
    }

    // --- authenticated and authorized ---

    @Test
    void remitterSeesOwnPasskeysWithoutKeyMaterial() throws Exception {
        MockHttpSession alice = registerAndLogin(ALICE, new PasskeyTestClient(mockMvc));
        registerAndLogin(BOB, new PasskeyTestClient(mockMvc));

        mockMvc.perform(get("/api/passkeys").session(alice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(credentialIdsOf(ALICE).get(0)))
                .andExpect(jsonPath("$[0].label").value("My passkey"))
                .andExpect(jsonPath("$[0].publicKey").doesNotExist())
                .andExpect(jsonPath("$[0].attestationObject").doesNotExist());
    }

    @Test
    void remitterCanDeleteOwnPasskeyButNotTheLastOne() throws Exception {
        MockHttpSession alice = registerAndLogin(ALICE, new PasskeyTestClient(mockMvc));
        new PasskeyTestClient(mockMvc).register(alice, "Second key").andExpect(status().isOk());
        List<String> ids = credentialIdsOf(ALICE);

        mockMvc.perform(delete("/api/passkeys/" + ids.get(1)).session(alice).with(csrf()))
                .andExpect(status().isNoContent());
        verify(mailSender).sendPasskeyRemovedNotice(ALICE);

        mockMvc.perform(delete("/api/passkeys/" + ids.get(0)).session(alice).with(csrf()))
                .andExpect(status().isConflict());
        assertThat(credentialIdsOf(ALICE)).containsExactly(ids.get(0));
    }

    // --- authenticated but unauthorized (another user's resource) ---

    @Test
    void remitterCannotDeleteAnotherRemittersPasskey() throws Exception {
        MockHttpSession alice = registerAndLogin(ALICE, new PasskeyTestClient(mockMvc));
        new PasskeyTestClient(mockMvc).register(alice, "Second key").andExpect(status().isOk());
        MockHttpSession bob = registerAndLogin(BOB, new PasskeyTestClient(mockMvc));
        new PasskeyTestClient(mockMvc).register(bob, "Second key").andExpect(status().isOk());
        String bobsCredential = credentialIdsOf(BOB).get(0);

        // Same response as for a credential that does not exist
        mockMvc.perform(delete("/api/passkeys/" + bobsCredential).session(alice).with(csrf()))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/passkeys/doesNotExist").session(alice).with(csrf()))
                .andExpect(status().isNotFound());

        assertThat(credentialIdsOf(BOB)).hasSize(2);
        verify(mailSender, never()).sendPasskeyRemovedNotice(anyString());
    }

    @Test
    void eachRemitterListsOnlyTheirOwnPasskeys() throws Exception {
        MockHttpSession alice = registerAndLogin(ALICE, new PasskeyTestClient(mockMvc));
        new PasskeyTestClient(mockMvc).register(alice, "Alice second").andExpect(status().isOk());
        MockHttpSession bob = registerAndLogin(BOB, new PasskeyTestClient(mockMvc));
        List<String> aliceIds = credentialIdsOf(ALICE);
        List<String> bobIds = credentialIdsOf(BOB);

        mockMvc.perform(get("/api/passkeys").session(alice))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[*].id", containsInAnyOrder(aliceIds.toArray())))
                .andExpect(jsonPath("$[*].id", not(hasItem(bobIds.get(0)))));
        mockMvc.perform(get("/api/passkeys").session(bob))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(bobIds.get(0)))
                .andExpect(jsonPath("$[*].id", not(hasItem(aliceIds.get(0)))))
                .andExpect(jsonPath("$[*].id", not(hasItem(aliceIds.get(1)))));
    }

    @Test
    void anotherRemittersLastPasskeyLooksMissingRatherThanProtected() throws Exception {
        MockHttpSession alice = registerAndLogin(ALICE, new PasskeyTestClient(mockMvc));
        registerAndLogin(BOB, new PasskeyTestClient(mockMvc));
        String bobsOnlyCredential = credentialIdsOf(BOB).get(0);

        // 404 (not 409 "last passkey"), so the response reveals nothing about Bob's account
        mockMvc.perform(delete("/api/passkeys/" + bobsOnlyCredential).session(alice).with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("The passkey was not found."));

        assertThat(credentialIdsOf(BOB)).containsExactly(bobsOnlyCredential);
        verify(mailSender, never()).sendPasskeyRemovedNotice(anyString());
    }

    @Test
    void passkeyRegisteredByARemitterIsAddedOnlyToTheirOwnAccount() throws Exception {
        MockHttpSession alice = registerAndLogin(ALICE, new PasskeyTestClient(mockMvc));
        registerAndLogin(BOB, new PasskeyTestClient(mockMvc));
        List<String> bobIdsBefore = credentialIdsOf(BOB);
        // Ignore the notices sent while setting up both accounts
        clearInvocations(mailSender);

        new PasskeyTestClient(mockMvc).register(alice, "Alice second").andExpect(status().isOk());

        assertThat(credentialIdsOf(ALICE)).hasSize(2);
        assertThat(credentialIdsOf(BOB)).isEqualTo(bobIdsBefore);
        verify(mailSender).sendPasskeyAddedNotice(ALICE);
        verify(mailSender, never()).sendPasskeyAddedNotice(BOB);
    }

    @Test
    void enrollmentSessionCannotManagePasskeys() throws Exception {
        registerAndLogin(BOB, new PasskeyTestClient(mockMvc));
        MockHttpSession enrollment = enrollNewRemitter(ALICE);

        mockMvc.perform(get("/api/passkeys").session(enrollment)).andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/passkeys/" + credentialIdsOf(BOB).get(0)).session(enrollment).with(csrf()))
                .andExpect(status().isForbidden());
    }

    // --- re-authentication, CSRF and input validation ---

    @Test
    void deletionRequiresRecentLogin() throws Exception {
        MockHttpSession alice = registerAndLogin(ALICE, new PasskeyTestClient(mockMvc));
        new PasskeyTestClient(mockMvc).register(alice, "Second key").andExpect(status().isOk());
        alice.setAttribute("com.crossborderpayment.security.AuthSessionManager.AUTHENTICATED_AT",
                Instant.now().minus(Duration.ofMinutes(6)));

        mockMvc.perform(delete("/api/passkeys/" + credentialIdsOf(ALICE).get(1)).session(alice).with(csrf()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(ProblemResponses.REAUTHENTICATION_REQUIRED));
        assertThat(credentialIdsOf(ALICE)).hasSize(2);
    }

    @Test
    void deletionRequiresCsrfToken() throws Exception {
        MockHttpSession alice = registerAndLogin(ALICE, new PasskeyTestClient(mockMvc));
        new PasskeyTestClient(mockMvc).register(alice, "Second key").andExpect(status().isOk());

        mockMvc.perform(delete("/api/passkeys/" + credentialIdsOf(ALICE).get(1)).session(alice))
                .andExpect(status().isForbidden());
        assertThat(credentialIdsOf(ALICE)).hasSize(2);
    }

    @Test
    void malformedCredentialIdIsRejected() throws Exception {
        MockHttpSession alice = registerAndLogin(ALICE, new PasskeyTestClient(mockMvc));

        mockMvc.perform(delete("/api/passkeys/abc$def").session(alice).with(csrf()))
                .andExpect(status().isBadRequest());
        mockMvc.perform(delete("/api/passkeys/" + "A".repeat(1365)).session(alice).with(csrf()))
                .andExpect(status().isBadRequest());
    }
}
