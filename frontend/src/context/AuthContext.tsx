import React, { createContext, useContext, useState, ReactNode, useCallback } from "react";
import { AuthContextType } from "../model/Auth";

export const AuthContext = createContext<AuthContextType | null>(null);

// The login state here only controls the UI; the backend session decides authorization.
// TODO: call the backend logout API in logout().
export const AuthProvider: React.FC<{ children: ReactNode }> = ({ children }) => {
    // Restore the UI login state after a reload (display only; the server decides authorization)
    const [isLoggedIn, setIsLoggedIn] = useState(() => sessionStorage.getItem("isLoggedIn") === "true");

    // Called after the backend accepted a passkey login
    const login = useCallback(() => {
        sessionStorage.setItem("isLoggedIn", "true");
        setIsLoggedIn(true);
    }, []);

    // To logout: clear all client-side login information
    const logout = useCallback(() => {
        sessionStorage.removeItem("isLoggedIn");
        sessionStorage.removeItem("emailAddr");
        setIsLoggedIn(false);
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
