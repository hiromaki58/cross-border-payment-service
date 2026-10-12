import { loginWithPasskey, registerPasskey } from "./Passkey";

const toBuffer = (bytes: number[]) => new Uint8Array(bytes).buffer;

const jsonResponse = (status: number, body: unknown = {}) =>
    new Response(JSON.stringify(body), { status, headers: { "Content-Type": "application/json" } });

describe("loginWithPasskey", () => {
    const fetchMock = vi.fn();
    const getMock = vi.fn();

    beforeEach(() => {
        fetchMock.mockReset();
        getMock.mockReset();
        vi.stubGlobal("fetch", fetchMock);
        vi.stubGlobal("PublicKeyCredential", function PublicKeyCredential() {});
        Object.defineProperty(navigator, "credentials", { value: { get: getMock }, configurable: true });
        document.cookie = "XSRF-TOKEN=csrf-token-value; path=/";
    });

    afterEach(() => {
        vi.unstubAllGlobals();
        document.cookie = "XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/";
    });

    const credential = {
        id: "AQID",
        rawId: toBuffer([1, 2, 3]),
        type: "public-key",
        response: {
            authenticatorData: toBuffer([4]),
            clientDataJSON: toBuffer([5]),
            signature: toBuffer([6]),
            userHandle: toBuffer([7]),
        },
        getClientExtensionResults: () => ({}),
    };

    test("sends the signed challenge with the CSRF header and returns success", async () => {
        fetchMock
            .mockResolvedValueOnce(jsonResponse(200, { challenge: "AAEC", rpId: "localhost", userVerification: "required" }))
            .mockResolvedValueOnce(jsonResponse(200, { authenticated: true }));
        getMock.mockResolvedValue(credential);

        await expect(loginWithPasskey()).resolves.toBe("success");

        const [optionsUrl, optionsInit] = fetchMock.mock.calls[0];
        expect(optionsUrl).toBe("/api/auth/passkey/login/options");
        expect(optionsInit).toMatchObject({ method: "POST", credentials: "include", headers: { "X-XSRF-TOKEN": "csrf-token-value" } });

        const publicKey = getMock.mock.calls[0][0].publicKey;
        expect(new Uint8Array(publicKey.challenge)).toEqual(new Uint8Array([0, 1, 2]));
        expect(publicKey.userVerification).toBe("required");

        const [loginUrl, loginInit] = fetchMock.mock.calls[1];
        expect(loginUrl).toBe("/api/auth/passkey/login");
        expect(loginInit.headers).toMatchObject({ "Content-Type": "application/json", "X-XSRF-TOKEN": "csrf-token-value" });
        expect(JSON.parse(loginInit.body)).toEqual({
            id: "AQID",
            rawId: "AQID",
            type: "public-key",
            response: { authenticatorData: "BA", clientDataJSON: "BQ", signature: "Bg", userHandle: "Bw" },
            clientExtensionResults: {},
        });
    });

    test("fetches the CSRF cookie first when it is missing", async () => {
        document.cookie = "XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/";
        fetchMock.mockImplementationOnce(async () => {
            document.cookie = "XSRF-TOKEN=issued-token; path=/";
            return jsonResponse(200);
        });
        fetchMock.mockResolvedValueOnce(jsonResponse(500));

        await expect(loginWithPasskey()).resolves.toBe("error");

        expect(fetchMock.mock.calls[0][0]).toBe("/api/auth/csrf");
        expect(fetchMock.mock.calls[1][1].headers).toEqual({ "X-XSRF-TOKEN": "issued-token" });
    });

    test("returns rejected when the server does not accept the passkey", async () => {
        fetchMock.mockResolvedValueOnce(jsonResponse(200, { challenge: "AAEC" })).mockResolvedValueOnce(jsonResponse(401));
        getMock.mockResolvedValue(credential);

        await expect(loginWithPasskey()).resolves.toBe("rejected");
    });

    test("returns cancelled when the user closes the passkey dialog", async () => {
        fetchMock.mockResolvedValueOnce(jsonResponse(200, { challenge: "AAEC" }));
        getMock.mockRejectedValue(new DOMException("cancelled", "NotAllowedError"));

        await expect(loginWithPasskey()).resolves.toBe("cancelled");
        expect(fetchMock).toHaveBeenCalledTimes(1);
    });

    test("returns error on a network failure", async () => {
        fetchMock.mockRejectedValue(new TypeError("Failed to fetch"));

        await expect(loginWithPasskey()).resolves.toBe("error");
    });

    test("returns unsupported when the browser has no WebAuthn", async () => {
        vi.stubGlobal("PublicKeyCredential", undefined);

        await expect(loginWithPasskey()).resolves.toBe("unsupported");
        expect(fetchMock).not.toHaveBeenCalled();
    });
});

describe("registerPasskey", () => {
    const fetchMock = vi.fn();
    const createMock = vi.fn();

    beforeEach(() => {
        fetchMock.mockReset();
        createMock.mockReset();
        vi.stubGlobal("fetch", fetchMock);
        vi.stubGlobal("PublicKeyCredential", function PublicKeyCredential() {});
        Object.defineProperty(navigator, "credentials", { value: { create: createMock }, configurable: true });
        document.cookie = "XSRF-TOKEN=csrf-token-value; path=/";
    });

    afterEach(() => {
        vi.unstubAllGlobals();
        document.cookie = "XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/";
    });

    const creationOptions = {
        rp: { id: "localhost", name: "Cross-Border Payment Service" },
        user: { id: "AQID", name: "taro@example.com", displayName: "Taro Yamada" },
        challenge: "AAEC",
        pubKeyCredParams: [{ type: "public-key", alg: -7 }],
        authenticatorSelection: { residentKey: "required", userVerification: "required" },
        excludeCredentials: [{ id: "BAU", type: "public-key" }],
    };

    const credential = {
        id: "AQID",
        rawId: toBuffer([1, 2, 3]),
        type: "public-key",
        response: {
            attestationObject: toBuffer([4]),
            clientDataJSON: toBuffer([5]),
            getTransports: () => ["internal"],
        },
        getClientExtensionResults: () => ({ credProps: { rk: true } }),
    };

    test("sends the new credential with its label and the CSRF header", async () => {
        fetchMock
            .mockResolvedValueOnce(jsonResponse(200, creationOptions))
            .mockResolvedValueOnce(jsonResponse(200, { success: true }));
        createMock.mockResolvedValue(credential);

        await expect(registerPasskey("My phone")).resolves.toBe("success");

        const [optionsUrl, optionsInit] = fetchMock.mock.calls[0];
        expect(optionsUrl).toBe("/api/auth/passkey/register/options");
        expect(optionsInit).toMatchObject({ method: "POST", credentials: "include", headers: { "X-XSRF-TOKEN": "csrf-token-value" } });

        const publicKey = createMock.mock.calls[0][0].publicKey;
        expect(new Uint8Array(publicKey.challenge)).toEqual(new Uint8Array([0, 1, 2]));
        expect(new Uint8Array(publicKey.user.id)).toEqual(new Uint8Array([1, 2, 3]));
        expect(new Uint8Array(publicKey.excludeCredentials[0].id)).toEqual(new Uint8Array([4, 5]));
        expect(publicKey.authenticatorSelection.userVerification).toBe("required");

        const [registerUrl, registerInit] = fetchMock.mock.calls[1];
        expect(registerUrl).toBe("/api/auth/passkey/register");
        expect(registerInit.headers).toMatchObject({ "Content-Type": "application/json", "X-XSRF-TOKEN": "csrf-token-value" });
        expect(JSON.parse(registerInit.body)).toEqual({
            publicKey: {
                label: "My phone",
                credential: {
                    id: "AQID",
                    rawId: "AQID",
                    type: "public-key",
                    response: { attestationObject: "BA", clientDataJSON: "BQ", transports: ["internal"] },
                    clientExtensionResults: { credProps: { rk: true } },
                },
            },
        });
    });

    test.each([
        [400, "rejected"],
        [401, "expired"],
        [500, "error"],
    ])("maps register status %i to %s", async (status, expected) => {
        fetchMock.mockResolvedValueOnce(jsonResponse(200, creationOptions)).mockResolvedValueOnce(jsonResponse(status));
        createMock.mockResolvedValue(credential);

        await expect(registerPasskey("My phone")).resolves.toBe(expected);
    });

    test.each([401, 403])("returns expired when the options request is refused with %i", async (status) => {
        fetchMock.mockResolvedValueOnce(jsonResponse(status));

        await expect(registerPasskey("My phone")).resolves.toBe("expired");
        expect(createMock).not.toHaveBeenCalled();
    });

    test("returns cancelled when the user closes the passkey dialog", async () => {
        fetchMock.mockResolvedValueOnce(jsonResponse(200, creationOptions));
        createMock.mockRejectedValue(new DOMException("cancelled", "NotAllowedError"));

        await expect(registerPasskey("My phone")).resolves.toBe("cancelled");
        expect(fetchMock).toHaveBeenCalledTimes(1);
    });

    test("returns unsupported when the browser has no WebAuthn", async () => {
        vi.stubGlobal("PublicKeyCredential", undefined);

        await expect(registerPasskey("My phone")).resolves.toBe("unsupported");
        expect(fetchMock).not.toHaveBeenCalled();
    });
});
