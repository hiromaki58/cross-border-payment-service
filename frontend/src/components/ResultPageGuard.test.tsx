import React from "react";
import { render, screen } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { AuthProvider } from "../context/AuthContext";
import ResultPageGuard, { RESULT_PAGE_STATE } from "./ResultPageGuard";

const renderAt = (initialEntry: string | { pathname: string; state: unknown }) =>
    render(
        <AuthProvider>
            <MemoryRouter initialEntries={[initialEntry]}>
                <Routes>
                    <Route path="/" element={<p>top page</p>} />
                    <Route path="/login" element={<p>login page</p>} />
                    <Route
                        path="/login/success"
                        element={
                            <ResultPageGuard>
                                <p>result page</p>
                            </ResultPageGuard>
                        }
                    />
                </Routes>
            </MemoryRouter>
        </AuthProvider>
    );

describe("ResultPageGuard", () => {
    afterEach(() => {
        sessionStorage.clear();
    });

    test("shows the result page after an in-app transition", () => {
        renderAt({ pathname: "/login/success", state: RESULT_PAGE_STATE });
        expect(screen.getByText("result page")).toBeTruthy();
    });

    test("redirects direct access to the login page when not logged in", () => {
        renderAt("/login/success");
        expect(screen.getByText("login page")).toBeTruthy();
        expect(screen.queryByText("result page")).toBeNull();
    });

    test("redirects direct access to the top page when logged in", () => {
        sessionStorage.setItem("isLoggedIn", "true");
        renderAt("/login/success");
        expect(screen.getByText("top page")).toBeTruthy();
        expect(screen.queryByText("result page")).toBeNull();
    });

    test("redirects when the navigation state is not the expected one", () => {
        renderAt({ pathname: "/login/success", state: { fromApp: "yes" } });
        expect(screen.getByText("login page")).toBeTruthy();
    });
});
