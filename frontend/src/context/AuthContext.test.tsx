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

    test("logout clears login state and e-mail address without calling a non-existent API", () => {
        sessionStorage.setItem("isLoggedIn", "true");
        sessionStorage.setItem("emailAddr", "user@example.com");
        const { result } = renderHook(() => useAuthProvider(), { wrapper });

        act(() => {
            result.current.logout();
        });

        expect(result.current.isLoggedIn).toBe(false);
        expect(sessionStorage.getItem("isLoggedIn")).toBeNull();
        expect(sessionStorage.getItem("emailAddr")).toBeNull();
        expect(fetchMock).not.toHaveBeenCalled();
    });

    test("throws when used outside AuthProvider", () => {
        vi.spyOn(console, "error").mockImplementation(() => {});

        expect(() => renderHook(() => useAuthProvider())).toThrow("useAuthProvider must be used within an AuthProvider");
    });
});
