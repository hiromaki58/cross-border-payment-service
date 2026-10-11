import React, { createContext, useContext, useState, ReactNode, useCallback } from "react";
import { AuthContextType } from "../model/Auth";
import { API_BASE_URL } from "../cmn/Constant";
import { csrfHeaders } from "../cmn/Csrf";

export const AuthContext = createContext<AuthContextType | null>(null);

// The login state here only controls the UI; the backend session decides authorization.
export const AuthProvider: React.FC<{ children: ReactNode }> = ({ children }) => {
    // Restore the UI login state after a reload (display only; the server decides authorization)
    const [isLoggedIn, setIsLoggedIn] = useState(() => sessionStorage.getItem("isLoggedIn") === "true");

    // Called after the backend accepted a passkey login
    const login = useCallback(() => {
        sessionStorage.setItem("isLoggedIn", "true");
        setIsLoggedIn(true);
    }, []);

    // To logout: invalidate the server session, then clear all client-side login information.
    // The client state is cleared even when the request fails (e.g. offline or the session already expired).
    const logout = useCallback(async () => {
        try {
            await fetch(`${API_BASE_URL}/api/auth/logout`, {
                method: "POST",
                credentials: "include",
                headers: await csrfHeaders(),
            });
        } catch {
            // Nothing more can be done on the client; the server session expires by its timeout
        } finally {
            sessionStorage.removeItem("isLoggedIn");
            sessionStorage.removeItem("emailAddr");
            setIsLoggedIn(false);
        }
    }, []);

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
