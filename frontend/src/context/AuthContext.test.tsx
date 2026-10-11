import React from "react";
import { act, renderHook } from "@testing-library/react";
import { AuthProvider, useAuthProvider } from "./AuthContext";

const wrapper = ({ children }: { children: React.ReactNode }) => <AuthProvider>{children}</AuthProvider>;

describe("AuthContext", () => {
    const fetchMock = vi.fn();

    beforeEach(() => {
        fetchMock.mockReset();
        vi.stubGlobal("fetch", fetchMock);
        sessionStorage.clear();
    });

    afterEach(() => {
        vi.unstubAllGlobals();
        document.cookie = "XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/";
    });

    test("is logged out by default", () => {
        const { result } = renderHook(() => useAuthProvider(), { wrapper });

        expect(result.current.isLoggedIn).toBe(false);
    });

    test("restores the login state after a reload", () => {
        sessionStorage.setItem("isLoggedIn", "true");
        const { result } = renderHook(() => useAuthProvider(), { wrapper });

        expect(result.current.isLoggedIn).toBe(true);
    });

    test("login stores the login state for the UI", () => {
        const { result } = renderHook(() => useAuthProvider(), { wrapper });

        act(() => {
            result.current.login();
        });

        expect(result.current.isLoggedIn).toBe(true);
        expect(sessionStorage.getItem("isLoggedIn")).toBe("true");
    });

    test("logout invalidates the server session with the CSRF token and clears the login state", async () => {
        document.cookie = "XSRF-TOKEN=csrf-token-value; path=/";
        fetchMock.mockResolvedValue(new Response(null, { status: 204 }));
        sessionStorage.setItem("isLoggedIn", "true");
        sessionStorage.setItem("emailAddr", "user@example.com");
        const { result } = renderHook(() => useAuthProvider(), { wrapper });

        await act(async () => {
            await result.current.logout();
        });

        expect(fetchMock).toHaveBeenCalledWith("/api/auth/logout", {
            method: "POST",
            credentials: "include",
            headers: { "X-XSRF-TOKEN": "csrf-token-value" },
        });
        expect(result.current.isLoggedIn).toBe(false);
        expect(sessionStorage.getItem("isLoggedIn")).toBeNull();
        expect(sessionStorage.getItem("emailAddr")).toBeNull();
    });

    test.each([
        ["a network failure", () => fetchMock.mockRejectedValue(new TypeError("Failed to fetch"))],
        ["a server error", () => fetchMock.mockResolvedValue(new Response(null, { status: 500 }))],
    ])("logout clears the login state even after %s", async (_, setUp) => {
        document.cookie = "XSRF-TOKEN=csrf-token-value; path=/";
        setUp();
        sessionStorage.setItem("isLoggedIn", "true");
        const { result } = renderHook(() => useAuthProvider(), { wrapper });

        await act(async () => {
            await result.current.logout();
        });

        expect(fetchMock).toHaveBeenCalled();
        expect(result.current.isLoggedIn).toBe(false);
        expect(sessionStorage.getItem("isLoggedIn")).toBeNull();
    });

    test("throws when used outside AuthProvider", () => {
        vi.spyOn(console, "error").mockImplementation(() => {});

        expect(() => renderHook(() => useAuthProvider())).toThrow("useAuthProvider must be used within an AuthProvider");
    });
});
