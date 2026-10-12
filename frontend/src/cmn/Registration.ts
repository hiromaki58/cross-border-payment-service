import { API_BASE_URL } from "./Constant";
import { csrfHeaders } from "./Csrf";

// Same limits as the backend (RegistrationRequest / EmailVerificationRequest)
export const EMAIL_MAX_LENGTH = 254;
export const NAME_MAX_LENGTH = 100;
export const CODE_PATTERN = /^[0-9]{8}$/;

export type RegistrationInput = {
    firstName: string;
    familyName: string;
    emailAddr: string;
};

// accepted: a code was sent if the address can be used (the same answer either way)
// invalid: the server rejected the input / error: network or unexpected server error
export type RegistrationRequestResult = "accepted" | "invalid" | "error";

// verified: the session may now register a passkey / invalid: wrong or expired code
export type EmailVerificationResult = "verified" | "invalid" | "error";

const postJson = async (path: string, body: unknown): Promise<Response> =>
    fetch(`${API_BASE_URL}${path}`, {
        method: "POST",
        credentials: "include",
        headers: { "Content-Type": "application/json", ...(await csrfHeaders()) },
        body: JSON.stringify(body),
    });

// Starts a registration: the server sends a one-time code to the e-mail address
export const requestRegistration = async (input: RegistrationInput): Promise<RegistrationRequestResult> => {
    try {
        const response = await postJson("/api/auth/registration", {
            firstName: input.firstName,
            familyName: input.familyName,
            emailAddr: input.emailAddr,
        });
        if (response.status === 202) {
            return "accepted";
        }
        return response.status === 400 ? "invalid" : "error";
    } catch {
        return "error";
    }
};

// Verifies the one-time code sent by e-mail (only in the browser session that requested it)
export const verifyEmail = async (code: string): Promise<EmailVerificationResult> => {
    try {
        const response = await postJson("/api/auth/email-verification", { code });
        if (response.ok) {
            return "verified";
        }
        return response.status === 400 ? "invalid" : "error";
    } catch {
        return "error";
    }
};
