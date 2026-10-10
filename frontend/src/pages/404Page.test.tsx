import React from "react";
import { render, screen } from "@testing-library/react";
import { MemoryRouter } from "react-router-dom";
import { AuthProvider } from "../context/AuthContext";
import NotFoundPage from "./404Page";

describe("NotFoundPage", () => {
    test("links back to the top page inside the app", () => {
        render(
            <AuthProvider>
                <MemoryRouter>
                    <NotFoundPage />
                </MemoryRouter>
            </AuthProvider>
        );

        expect(screen.getByRole("link", { name: "Back to top page" })).toHaveAttribute("href", "/");
    });
});
