import { API_BASE_URL } from "./Constant";
import { csrfHeaders } from "./Csrf";

// Passkey endpoints on the backend (PasskeyEndpoints)
const LOGIN_OPTIONS_URL = `${API_BASE_URL}/api/auth/passkey/login/options`;
const LOGIN_URL = `${API_BASE_URL}/api/auth/passkey/login`;
const REGISTER_OPTIONS_URL = `${API_BASE_URL}/api/auth/passkey/register/options`;
const REGISTER_URL = `${API_BASE_URL}/api/auth/passkey/register`;

// Same limits as the backend (VerifyingUserCredentialRepository)
export const PASSKEY_LABEL_MAX_LENGTH = 64;

// success: logged in / cancelled: the user closed or timed out the browser's passkey dialog
// rejected: the server did not accept the passkey / unsupported: no WebAuthn in this browser
// error: network or unexpected server error
export type PasskeyLoginResult = "success" | "cancelled" | "rejected" | "unsupported" | "error";

// success: registered (the enrollment session has ended; the user logs in with the new passkey)
// rejected: the server did not accept the passkey / expired: the e-mail verified session has ended
export type PasskeyRegistrationResult = "success" | "cancelled" | "rejected" | "expired" | "unsupported" | "error";

type CredentialDescriptorJSON = { id: string; type: PublicKeyCredentialType; transports?: AuthenticatorTransport[] };

type CreationOptionsJSON = {
    rp: { id?: string; name: string };
    user: { id: string; name: string; displayName: string };
    challenge: string;
    pubKeyCredParams: PublicKeyCredentialParameters[];
    timeout?: number;
    excludeCredentials?: CredentialDescriptorJSON[];
    authenticatorSelection?: AuthenticatorSelectionCriteria;
    attestation?: AttestationConveyancePreference;
    extensions?: AuthenticationExtensionsClientInputs;
};

type RequestOptionsJSON = {
    challenge: string;
    timeout?: number;
    rpId?: string;
    allowCredentials?: CredentialDescriptorJSON[];
    userVerification?: UserVerificationRequirement;
};

const base64UrlToBuffer = (value: string): ArrayBuffer => {
    const base64 = value.replace(/-/g, "+").replace(/_/g, "/").padEnd(Math.ceil(value.length / 4) * 4, "=");
    const binary = atob(base64);
    const bytes = new Uint8Array(binary.length);
    for (let i = 0; i < binary.length; i++) {
        bytes[i] = binary.charCodeAt(i);
    }
    return bytes.buffer;
};

const bufferToBase64Url = (buffer: ArrayBuffer): string => {
    const bytes = new Uint8Array(buffer);
    let binary = "";
    for (let i = 0; i < bytes.length; i++) {
        binary += String.fromCharCode(bytes[i]);
    }
    return btoa(binary).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
};

const toDescriptors = (descriptors: CredentialDescriptorJSON[] | undefined): PublicKeyCredentialDescriptor[] =>
    (descriptors ?? []).map((credential) => ({
        id: base64UrlToBuffer(credential.id),
        type: credential.type,
        transports: credential.transports,
    }));

// Only the fields the browser needs are copied; values come from the server, not the user
const toRequestOptions = (json: RequestOptionsJSON): PublicKeyCredentialRequestOptions => ({
    challenge: base64UrlToBuffer(json.challenge),
    timeout: json.timeout,
    rpId: json.rpId,
    userVerification: json.userVerification,
    allowCredentials: toDescriptors(json.allowCredentials),
});

const toCreationOptions = (json: CreationOptionsJSON): PublicKeyCredentialCreationOptions => ({
    rp: json.rp,
    user: { id: base64UrlToBuffer(json.user.id), name: json.user.name, displayName: json.user.displayName },
    challenge: base64UrlToBuffer(json.challenge),
    pubKeyCredParams: json.pubKeyCredParams,
    timeout: json.timeout,
    excludeCredentials: toDescriptors(json.excludeCredentials),
    authenticatorSelection: json.authenticatorSelection,
    attestation: json.attestation,
    extensions: json.extensions,
});

const isCancelled = (e: unknown): boolean =>
    e instanceof DOMException && (e.name === "NotAllowedError" || e.name === "AbortError");

const isPasskeySupported = (): boolean =>
    typeof window !== "undefined" && typeof window.PublicKeyCredential === "function" && !!navigator.credentials;

/**
 * Logs in with a passkey: gets a challenge, asks the browser to sign it, and sends the assertion.
 * The server verifies the signature, origin and RP ID and creates the session.
 */
export const loginWithPasskey = async (): Promise<PasskeyLoginResult> => {
    if (!isPasskeySupported()) {
        return "unsupported";
    }
    try {
        const optionsResponse = await fetch(LOGIN_OPTIONS_URL, {
            method: "POST",
            credentials: "include",
            headers: await csrfHeaders(),
        });
        if (!optionsResponse.ok) {
            return "error";
        }
        const options = (await optionsResponse.json()) as RequestOptionsJSON;

        let credential: PublicKeyCredential | null;
        try {
            credential = (await navigator.credentials.get({ publicKey: toRequestOptions(options) })) as PublicKeyCredential | null;
        } catch (e) {
            if (isCancelled(e)) {
                return "cancelled";
            }
            throw e;
        }
        if (!credential) {
            return "cancelled";
        }

        const assertion = credential.response as AuthenticatorAssertionResponse;
        const loginResponse = await fetch(LOGIN_URL, {
            method: "POST",
            credentials: "include",
            headers: { "Content-Type": "application/json", ...(await csrfHeaders()) },
            body: JSON.stringify({
                id: credential.id,
                rawId: bufferToBase64Url(credential.rawId),
                type: credential.type,
                response: {
                    authenticatorData: bufferToBase64Url(assertion.authenticatorData),
                    clientDataJSON: bufferToBase64Url(assertion.clientDataJSON),
                    signature: bufferToBase64Url(assertion.signature),
                    userHandle: assertion.userHandle ? bufferToBase64Url(assertion.userHandle) : null,
                },
                clientExtensionResults: credential.getClientExtensionResults(),
            }),
        });
        if (loginResponse.status === 401) {
            return "rejected";
        }
        return loginResponse.ok ? "success" : "error";
    } catch {
        // Details are not shown to the user
        return "error";
    }
};

/**
 * Registers a passkey in the e-mail verified enrollment session: gets the creation options,
 * asks the browser to create the credential, and sends it with its label.
 * The server verifies the attestation, origin and RP ID and stores the passkey.
 */
export const registerPasskey = async (label: string): Promise<PasskeyRegistrationResult> => {
    if (!isPasskeySupported()) {
        return "unsupported";
    }
    try {
        const optionsResponse = await fetch(REGISTER_OPTIONS_URL, {
            method: "POST",
            credentials: "include",
            headers: await csrfHeaders(),
        });
        if (optionsResponse.status === 401 || optionsResponse.status === 403) {
            return "expired";
        }
        if (!optionsResponse.ok) {
            return "error";
        }
        const options = (await optionsResponse.json()) as CreationOptionsJSON;

        let credential: PublicKeyCredential | null;
        try {
            credential = (await navigator.credentials.create({ publicKey: toCreationOptions(options) })) as PublicKeyCredential | null;
        } catch (e) {
            if (isCancelled(e)) {
                return "cancelled";
            }
            throw e;
        }
        if (!credential) {
            return "cancelled";
        }

        const attestation = credential.response as AuthenticatorAttestationResponse & { getTransports?: () => string[] };
        const registerResponse = await fetch(REGISTER_URL, {
            method: "POST",
            credentials: "include",
            headers: { "Content-Type": "application/json", ...(await csrfHeaders()) },
            body: JSON.stringify({
                publicKey: {
                    label,
                    credential: {
                        id: credential.id,
                        rawId: bufferToBase64Url(credential.rawId),
                        type: credential.type,
                        response: {
                            attestationObject: bufferToBase64Url(attestation.attestationObject),
                            clientDataJSON: bufferToBase64Url(attestation.clientDataJSON),
                            transports: attestation.getTransports ? attestation.getTransports() : [],
                        },
                        clientExtensionResults: credential.getClientExtensionResults(),
                    },
                },
            }),
        });
        if (registerResponse.status === 400) {
            return "rejected";
        }
        if (registerResponse.status === 401 || registerResponse.status === 403) {
            return "expired";
        }
        return registerResponse.ok ? "success" : "error";
    } catch {
        // Details are not shown to the user
        return "error";
    }
};
