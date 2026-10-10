import React from "react";
import { act, renderHook } from "@testing-library/react";
import { AuthProvider, useAuthProvider } from "./AuthContext";

const wrapper = ({ children }: { children: React.ReactNode }) => <AuthProvider>{children}</AuthProvider>;

const jsonResponse = (ok: boolean, body: unknown) =>
    ({ ok, json: async () => body }) as unknown as Response;

const nonJsonResponse = () =>
    ({ ok: true, json: async () => { throw new SyntaxError("Unexpected token <"); } }) as unknown as Response;

describe("AuthContext", () => {
    const fetchMock = vi.fn();

    beforeEach(() => {
        fetchMock.mockReset();
        vi.stubGlobal("fetch", fetchMock);
        sessionStorage.clear();
    });

    afterEach(() => {
        vi.useRealTimers();
        vi.unstubAllGlobals();
    });

    test("login succeeds: sets logged-in state and sends credentials", async () => {
        fetchMock.mockResolvedValue(jsonResponse(true, { loginTry: true }));
        const { result } = renderHook(() => useAuthProvider(), { wrapper });

        await act(async () => {
            await result.current.login("user@example.com", "pass");
        });

        expect(result.current.isLoggedIn).toBe(true);
        expect(sessionStorage.getItem("isLoggedIn")).toBe("true");
        expect(sessionStorage.getItem("emailAddr")).toBe("user@example.com");
        expect(fetchMock).toHaveBeenCalledWith(
            "/api/player/login",
            expect.objectContaining({ method: "POST", credentials: "include" })
        );
    });

    test.each([
        ["HTTP error", () => jsonResponse(false, { loginTry: true })],
        ["loginTry is false", () => jsonResponse(true, { loginTry: false })],
        ["non-JSON response", () => nonJsonResponse()],
    ])("login fails on %s: throws and stays logged out", async (_label, makeResponse) => {
        fetchMock.mockResolvedValue(makeResponse());
        const { result } = renderHook(() => useAuthProvider(), { wrapper });

        await act(async () => {
            await expect(result.current.login("user@example.com", "wrong")).rejects.toThrow("Login failed");
        });

        expect(result.current.isLoggedIn).toBe(false);
        expect(sessionStorage.getItem("isLoggedIn")).toBeNull();
        expect(sessionStorage.getItem("emailAddr")).toBeNull();
    });

    test("login fails on network error: throws and stays logged out", async () => {
        fetchMock.mockRejectedValue(new TypeError("Failed to fetch"));
        const { result } = renderHook(() => useAuthProvider(), { wrapper });

        await act(async () => {
            await expect(result.current.login("user@example.com", "pass")).rejects.toThrow();
        });

        expect(result.current.isLoggedIn).toBe(false);
    });

    test("logout clears login state and e-mail address and sends credentials", async () => {
        fetchMock.mockResolvedValue(jsonResponse(true, { loginTry: true }));
        const { result } = renderHook(() => useAuthProvider(), { wrapper });

        await act(async () => {
            await result.current.login("user@example.com", "pass");
        });
        act(() => {
            result.current.logout();
        });

        expect(result.current.isLoggedIn).toBe(false);
        expect(sessionStorage.getItem("isLoggedIn")).toBeNull();
        expect(sessionStorage.getItem("emailAddr")).toBeNull();
        expect(fetchMock).toHaveBeenLastCalledWith(
            "/api/player/logout",
            expect.objectContaining({ method: "POST", credentials: "include" })
        );
    });

    test("old session timer does not log out after logout and re-login", async () => {
        vi.useFakeTimers();
        fetchMock.mockResolvedValue(jsonResponse(true, { loginTry: true }));
        const { result } = renderHook(() => useAuthProvider(), { wrapper });
        const minutes = (m: number) => m * 60 * 1000;

        await act(async () => {
            await result.current.login("user@example.com", "pass");
        });
        act(() => {
            vi.advanceTimersByTime(minutes(30));
            result.current.logout();
        });
        await act(async () => {
            await result.current.login("user@example.com", "pass");
        });

        // 61 minutes after the first login: the first timer must not fire
        act(() => {
            vi.advanceTimersByTime(minutes(31));
        });
        expect(result.current.isLoggedIn).toBe(true);

        // 60 minutes after the second login: the session times out
        act(() => {
            vi.advanceTimersByTime(minutes(29));
        });
        expect(result.current.isLoggedIn).toBe(false);
        expect(sessionStorage.getItem("emailAddr")).toBeNull();
    });
});
