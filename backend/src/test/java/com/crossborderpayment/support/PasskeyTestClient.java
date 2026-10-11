package com.crossborderpayment.support;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.webauthn4j.data.AttestationConveyancePreference;
import com.webauthn4j.data.AuthenticatorAssertionResponse;
import com.webauthn4j.data.AuthenticatorAttestationResponse;
import com.webauthn4j.data.AuthenticatorSelectionCriteria;
import com.webauthn4j.data.PublicKeyCredential;
import com.webauthn4j.data.PublicKeyCredentialCreationOptions;
import com.webauthn4j.data.PublicKeyCredentialDescriptor;
import com.webauthn4j.data.PublicKeyCredentialParameters;
import com.webauthn4j.data.PublicKeyCredentialRequestOptions;
import com.webauthn4j.data.PublicKeyCredentialRpEntity;
import com.webauthn4j.data.PublicKeyCredentialType;
import com.webauthn4j.data.PublicKeyCredentialUserEntity;
import com.webauthn4j.data.ResidentKeyRequirement;
import com.webauthn4j.data.UserVerificationRequirement;
import com.webauthn4j.data.attestation.statement.COSEAlgorithmIdentifier;
import com.webauthn4j.data.client.Origin;
import com.webauthn4j.data.client.challenge.DefaultChallenge;
import com.webauthn4j.test.authenticator.webauthn.PackedAuthenticator;
import com.webauthn4j.test.authenticator.webauthn.WebAuthnAuthenticatorAdaptor;
import com.webauthn4j.test.client.ClientPlatform;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * Plays the browser + authenticator side of the passkey ceremonies against the real endpoints,
 * using the webauthn4j authenticator emulator (real key pairs and signatures).
 */
public final class PasskeyTestClient {

    public static final String ORIGIN = "http://localhost:3000";

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final Base64.Encoder B64URL = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder B64URL_DECODER = Base64.getUrlDecoder();

    private final MockMvc mockMvc;
    private final ClientPlatform platform;

    public PasskeyTestClient(MockMvc mockMvc) {
        this(mockMvc, ORIGIN);
    }

    public PasskeyTestClient(MockMvc mockMvc, String origin) {
        this.mockMvc = mockMvc;
        this.platform = new ClientPlatform(new Origin(origin), new WebAuthnAuthenticatorAdaptor(new PackedAuthenticator()));
    }

    // Emulates the same authenticator being used from another site (e.g. a phishing page)
    public void setOrigin(String origin) {
        platform.setOrigin(new Origin(origin));
    }

    public ResultActions requestRegistrationOptions(MockHttpSession session) throws Exception {
        return mockMvc.perform(post("/api/auth/passkey/register/options").session(session).with(csrf()));
    }

    public JsonNode registrationOptions(MockHttpSession session) throws Exception {
        String body = requestRegistrationOptions(session).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JSON.readTree(body);
    }

    /**
     * Creates a passkey on the emulated authenticator and sends it to POST /webauthn/register.
     */
    public ResultActions register(MockHttpSession session, String label) throws Exception {
        JsonNode options = registrationOptions(session);
        PublicKeyCredential<AuthenticatorAttestationResponse, ?> credential = platform.create(toCreationOptions(options));

        ObjectNode response = JSON.createObjectNode()
                .put("attestationObject", b64(credential.getResponse().getAttestationObject()))
                .put("clientDataJSON", b64(credential.getResponse().getClientDataJSON()));
        response.putArray("transports").add("internal").add("hybrid");
        ObjectNode credentialJson = JSON.createObjectNode()
                .put("id", b64(credential.getRawId()))
                .put("rawId", b64(credential.getRawId()))
                .put("type", "public-key")
                .put("authenticatorAttachment", "platform");
        credentialJson.set("response", response);
        credentialJson.putObject("clientExtensionResults");
        ObjectNode publicKey = JSON.createObjectNode().put("label", label);
        publicKey.set("credential", credentialJson);
        ObjectNode body = JSON.createObjectNode();
        body.set("publicKey", publicKey);

        return mockMvc.perform(post("/api/auth/passkey/register").session(session).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(JSON.writeValueAsString(body)));
    }

    public ResultActions login(MockHttpSession session) throws Exception {
        return login(session, true);
    }

    /**
     * @param performUserVerification false emulates an authenticator / client that skipped user
     *                                verification although the server required it
     */
    public ResultActions login(MockHttpSession session, boolean performUserVerification) throws Exception {
        String optionsBody = mockMvc.perform(post("/api/auth/passkey/login/options").session(session).with(csrf()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        JsonNode options = JSON.readTree(optionsBody);

        PublicKeyCredentialRequestOptions requestOptions = new PublicKeyCredentialRequestOptions(
                new DefaultChallenge(decode(options.get("challenge").asString())),
                options.get("timeout").asLong(),
                options.get("rpId").asString(),
                descriptors(options.get("allowCredentials")),
                performUserVerification ? UserVerificationRequirement.REQUIRED : UserVerificationRequirement.DISCOURAGED,
                null);
        PublicKeyCredential<AuthenticatorAssertionResponse, ?> credential = platform.get(requestOptions);

        ObjectNode response = JSON.createObjectNode()
                .put("authenticatorData", b64(credential.getResponse().getAuthenticatorData()))
                .put("clientDataJSON", b64(credential.getResponse().getClientDataJSON()))
                .put("signature", b64(credential.getResponse().getSignature()))
                .put("userHandle", b64(credential.getResponse().getUserHandle()));
        ObjectNode body = JSON.createObjectNode()
                .put("id", b64(credential.getRawId()))
                .put("rawId", b64(credential.getRawId()))
                .put("type", "public-key")
                .put("authenticatorAttachment", "platform");
        body.set("response", response);
        body.putObject("clientExtensionResults");

        return mockMvc.perform(post("/api/auth/passkey/login").session(session).with(csrf())
                .contentType(MediaType.APPLICATION_JSON).content(JSON.writeValueAsString(body)));
    }

    private static PublicKeyCredentialCreationOptions toCreationOptions(JsonNode options) {
        JsonNode rp = options.get("rp");
        JsonNode user = options.get("user");
        JsonNode selection = options.get("authenticatorSelection");
        return new PublicKeyCredentialCreationOptions(
                new PublicKeyCredentialRpEntity(rp.get("id").asString(), rp.get("name").asString()),
                new PublicKeyCredentialUserEntity(decode(user.get("id").asString()), user.get("name").asString(),
                        user.get("displayName").asString()),
                new DefaultChallenge(decode(options.get("challenge").asString())),
                List.of(new PublicKeyCredentialParameters(PublicKeyCredentialType.PUBLIC_KEY, COSEAlgorithmIdentifier.ES256)),
                options.get("timeout").asLong(),
                descriptors(options.get("excludeCredentials")),
                new AuthenticatorSelectionCriteria(null,
                        ResidentKeyRequirement.create(selection.get("residentKey").asString()),
                        UserVerificationRequirement.create(selection.get("userVerification").asString())),
                AttestationConveyancePreference.NONE,
                null);
    }

    private static List<PublicKeyCredentialDescriptor> descriptors(JsonNode array) {
        List<PublicKeyCredentialDescriptor> result = new ArrayList<>();
        if (array != null) {
            for (JsonNode descriptor : array) {
                result.add(new PublicKeyCredentialDescriptor(PublicKeyCredentialType.PUBLIC_KEY,
                        decode(descriptor.get("id").asString()), null));
            }
        }
        return result;
    }

    private static String b64(byte[] bytes) {
        return B64URL.encodeToString(bytes);
    }

    private static byte[] decode(String value) {
        return B64URL_DECODER.decode(value);
    }
}
