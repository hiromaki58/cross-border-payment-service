import React from "react";
import { render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { AuthProvider } from "../context/AuthContext";
import HomePage from "./IndexPage";

const renderPage = () =>
    render(
        <AuthProvider>
            <MemoryRouter>
                <HomePage />
            </MemoryRouter>
        </AuthProvider>
    );

describe("HomePage", () => {
    beforeEach(() => {
        sessionStorage.clear();
    });

    test("shows the service title and the login prompt before login", () => {
        renderPage();

        expect(screen.getByRole("heading", { name: "Cross-Border Payment Service" })).toBeInTheDocument();
        expect(screen.getByRole("heading", { name: /Log in with a passkey/ })).toBeInTheDocument();
        expect(screen.getByRole("link", { name: "Go to login page" })).toHaveAttribute("href", "/login");
    });

    test("does not show the login prompt after login", () => {
        sessionStorage.setItem("isLoggedIn", "true");
        renderPage();

        expect(screen.getByRole("heading", { name: "Cross-Border Payment Service" })).toBeInTheDocument();
        expect(screen.queryByRole("heading", { name: /Log in with a passkey/ })).not.toBeInTheDocument();
        expect(screen.queryByRole("link", { name: "Go to login page" })).not.toBeInTheDocument();
    });
});
