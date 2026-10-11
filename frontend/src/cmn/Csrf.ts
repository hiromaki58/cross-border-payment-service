import { API_BASE_URL } from "./Constant";

// Names used by the backend (SessionSecurityConfig)
const CSRF_COOKIE_NAME = "XSRF-TOKEN";
const CSRF_HEADER_NAME = "X-XSRF-TOKEN";

const readCsrfCookie = (): string | null => {
    const prefix = `${CSRF_COOKIE_NAME}=`;
    const cookie = document.cookie.split("; ").find((entry) => entry.startsWith(prefix));
    return cookie ? decodeURIComponent(cookie.substring(prefix.length)) : null;
};

// Returns the header carrying the CSRF token for a POST to the API.
// The cookie is issued by GET /api/auth/csrf before the first POST.
export const csrfHeaders = async (): Promise<Record<string, string>> => {
    let token = readCsrfCookie();
    if (!token) {
        const response = await fetch(`${API_BASE_URL}/api/auth/csrf`, { credentials: "include" });
        if (!response.ok) {
            throw new Error("Could not obtain a CSRF token");
        }
        token = readCsrfCookie();
    }
    if (!token) {
        throw new Error("Could not obtain a CSRF token");
    }
    return { [CSRF_HEADER_NAME]: token };
};
