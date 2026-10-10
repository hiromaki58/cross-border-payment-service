import React, { createContext, useContext, useState, ReactNode, useCallback, useRef } from "react";
import { API_BASE_URL } from "../cmn/Constant";
import { AuthContextType } from "../model/Auth";

export const AuthContext = createContext<AuthContextType | null>(null);

const SESSION_TIMEOUT_MS = 60 * 60 * 1000;

export const AuthProvider: React.FC<{ children: ReactNode }> = ({ children }) => {
    // Restore the UI login state after a reload (display only; the server decides authorization)
    const [isLoggedIn, setIsLoggedIn] = useState(() => sessionStorage.getItem("isLoggedIn") === "true");
    const sessionTimer = useRef<ReturnType<typeof setTimeout> | null>(null);

    // Clear all client-side login information
    const clearLoginState = useCallback(() => {
        if (sessionTimer.current) {
            clearTimeout(sessionTimer.current);
            sessionTimer.current = null;
        }
        sessionStorage.removeItem("isLoggedIn");
        sessionStorage.removeItem("emailAddr");
        setIsLoggedIn(false);
    }, []);

    // Session time out
    const startSessionTimer = useCallback(() => {
        if (sessionTimer.current) {
            clearTimeout(sessionTimer.current);
        }
        sessionTimer.current = setTimeout(clearLoginState, SESSION_TIMEOUT_MS);
    }, [clearLoginState]);

    // To login
    const login = useCallback(async (emailAddr: string, password: string) => {
        const response = await fetch(`${API_BASE_URL}/api/player/login`, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            credentials: "include",
            body: JSON.stringify({ emailAddr, password }),
        });

        // Treat HTTP errors, non-JSON bodies, and loginTry=false all as a failed login
        const result = response.ok ? await response.json().catch(() => null) : null;
        if (!result?.loginTry) {
            throw new Error("Login failed");
        }

        setIsLoggedIn(true);
        sessionStorage.setItem("isLoggedIn", "true");
        sessionStorage.setItem("emailAddr", emailAddr);
        startSessionTimer();
    }, [startSessionTimer]);

    // To logout
    const logout = useCallback(() => {
        clearLoginState();

        fetch(`${API_BASE_URL}/api/player/logout`, {
            method: "POST",
            credentials: "include",
        }).catch(() => console.error("Logout request failed"));
    }, [clearLoginState]);

    return (
        <AuthContext.Provider value={{ isLoggedIn, login, logout }}>
            {children}
        </AuthContext.Provider>
    );
};

export const useAuthProvider = (): AuthContextType => {
    const context = useContext(AuthContext);
    if (!context) {
        throw new Error("useAuthProvider must be used within an AuthProvider");
    }
    return context;
};
