import React, { createContext, useContext, useState, ReactNode, useCallback } from "react";
import { API_BASE_URL } from "src/cmn/Constant";
import { AuthContextType } from "src/model/Auth";

export const AuthContext = createContext<AuthContextType | null>(null);

export const AuthProvider: React.FC<{ children: ReactNode }> = ({ children }) => {
    const [isLoggedIn, setIsLoggedIn] = useState(false);

    // To login
    const login = useCallback(async (emailAddr: string, password: string) => {
        try {
            const response = await fetch(`${API_BASE_URL}/api/player/login`, {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify({ emailAddr, password }),
            });

            const result = await response.json();

            if (result.loginTry) {
                setIsLoggedIn(true);
                sessionStorage.setItem("isLoggedIn", "true");
                sessionStorage.setItem("emailAddr", emailAddr);

                // Session time out
                setTimeout(() => {
                sessionStorage.removeItem("isLoggedIn");
                sessionStorage.removeItem("emailAddr");
                setIsLoggedIn(false);
                }, 60 * 60 * 1000);
            }
            else {
                throw new Error(result.message || "Login failed");
            }
        }
        catch (err) {
            console.error("Login error:", err);
        }
    }, []);

    // To logout
    const logout = () => {
        sessionStorage.removeItem("isLoggedIn");
        setIsLoggedIn(false);

        fetch(`${API_BASE_URL}/api/player/logout`, { method: "POST" }).catch((err) =>
            console.error("Logout error:", err)
        );
    };

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
