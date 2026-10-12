import { requestRegistration, verifyEmail } from "./Registration";

describe("registration API", () => {
    const fetchMock = vi.fn();

    beforeEach(() => {
        fetchMock.mockReset();
        vi.stubGlobal("fetch", fetchMock);
        document.cookie = "XSRF-TOKEN=csrf-token-value; path=/";
    });

    afterEach(() => {
        vi.unstubAllGlobals();
        document.cookie = "XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/";
    });

    const input = { firstName: "Taro", familyName: "Yamada", emailAddr: "taro@example.com" };

    test("requestRegistration posts only the expected fields with the CSRF header", async () => {
        fetchMock.mockResolvedValue(new Response("{}", { status: 202 }));

        await expect(requestRegistration({ ...input, extra: "ignored" } as typeof input)).resolves.toBe("accepted");

        const [url, init] = fetchMock.mock.calls[0];
        expect(url).toBe("/api/auth/registration");
        expect(init).toMatchObject({
            method: "POST",
            credentials: "include",
            headers: { "Content-Type": "application/json", "X-XSRF-TOKEN": "csrf-token-value" },
        });
        expect(JSON.parse(init.body)).toEqual(input);
    });

    test.each([
        [400, "invalid"],
        [500, "error"],
    ])("requestRegistration maps status %i to %s", async (status, expected) => {
        fetchMock.mockResolvedValue(new Response(null, { status }));

        await expect(requestRegistration(input)).resolves.toBe(expected);
    });

    test("requestRegistration returns error on a network failure", async () => {
        fetchMock.mockRejectedValue(new TypeError("Failed to fetch"));

        await expect(requestRegistration(input)).resolves.toBe("error");
    });

    test("verifyEmail posts the code", async () => {
        fetchMock.mockResolvedValue(new Response(JSON.stringify({ stage: "PASSKEY_ENROLLMENT" }), { status: 200 }));

        await expect(verifyEmail("12345678")).resolves.toBe("verified");

        const [url, init] = fetchMock.mock.calls[0];
        expect(url).toBe("/api/auth/email-verification");
        expect(init.headers).toMatchObject({ "X-XSRF-TOKEN": "csrf-token-value" });
        expect(JSON.parse(init.body)).toEqual({ code: "12345678" });
    });

    test.each([
        [400, "invalid"],
        [500, "error"],
    ])("verifyEmail maps status %i to %s", async (status, expected) => {
        fetchMock.mockResolvedValue(new Response(null, { status }));

        await expect(verifyEmail("12345678")).resolves.toBe(expected);
    });
});
