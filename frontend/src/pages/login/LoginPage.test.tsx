import React from "react";
import { render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { AuthProvider } from "../../context/AuthContext";
import LoginPage from "./LoginPage";

describe("LoginPage", () => {
    afterEach(() => {
        vi.unstubAllGlobals();
    });

    test("shows that Google / one-time code login is coming soon", () => {
        render(
            <AuthProvider>
                <MemoryRouter>
                    <LoginPage />
                </MemoryRouter>
            </AuthProvider>
        );

        expect(screen.getByRole("heading", { name: "Login" })).toBeInTheDocument();
        expect(screen.getByText(/coming soon/)).toBeInTheDocument();
    });

    test("has no password input, password reset link, or API call", () => {
        const fetchMock = vi.fn();
        vi.stubGlobal("fetch", fetchMock);

        const { container } = render(
            <AuthProvider>
                <MemoryRouter>
                    <LoginPage />
                </MemoryRouter>
            </AuthProvider>
        );

        expect(container.querySelector('input[type="password"]')).toBeNull();
        expect(screen.queryByText(/forget password/i)).not.toBeInTheDocument();
        expect(fetchMock).not.toHaveBeenCalled();
    });
});
