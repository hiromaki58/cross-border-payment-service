import { API_BASE_URL } from "./Constant";
import { csrfHeaders } from "./Csrf";

// Passkey endpoints on the backend (PasskeyEndpoints)
const LOGIN_OPTIONS_URL = `${API_BASE_URL}/api/auth/passkey/login/options`;
const LOGIN_URL = `${API_BASE_URL}/api/auth/passkey/login`;

// success: logged in / cancelled: the user closed or timed out the browser's passkey dialog
// rejected: the server did not accept the passkey / unsupported: no WebAuthn in this browser
// error: network or unexpected server error
export type PasskeyLoginResult = "success" | "cancelled" | "rejected" | "unsupported" | "error";

type RequestOptionsJSON = {
    challenge: string;
    timeout?: number;
    rpId?: string;
    allowCredentials?: { id: string; type: PublicKeyCredentialType; transports?: AuthenticatorTransport[] }[];
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

// Only the fields the browser needs are copied; values come from the server, not the user
const toRequestOptions = (json: RequestOptionsJSON): PublicKeyCredentialRequestOptions => ({
    challenge: base64UrlToBuffer(json.challenge),
    timeout: json.timeout,
    rpId: json.rpId,
    userVerification: json.userVerification,
    allowCredentials: (json.allowCredentials ?? []).map((credential) => ({
        id: base64UrlToBuffer(credential.id),
        type: credential.type,
        transports: credential.transports,
    })),
});

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
            if (e instanceof DOMException && (e.name === "NotAllowedError" || e.name === "AbortError")) {
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
