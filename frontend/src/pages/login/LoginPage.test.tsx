import React from "react";
import { fireEvent, render, screen } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { AuthProvider } from "../../context/AuthContext";
import LoginPage from "./LoginPage";

const renderLoginPage = () =>
    render(
        <AuthProvider>
            <MemoryRouter initialEntries={["/login"]}>
                <Routes>
                    <Route path="/login" element={<LoginPage />} />
                    <Route path="/login/success" element={<p>success page</p>} />
                    <Route path="/login/fail" element={<p>fail page</p>} />
                </Routes>
            </MemoryRouter>
        </AuthProvider>
    );

const submitLogin = () => {
    fireEvent.change(screen.getByPlaceholderText("E-mail"), { target: { value: "user@example.com" } });
    fireEvent.change(screen.getByPlaceholderText("password"), { target: { value: "pass" } });
    fireEvent.click(screen.getByRole("button", { name: "login" }));
};

describe("LoginPage", () => {
    beforeEach(() => {
        sessionStorage.clear();
    });

    afterEach(() => {
        vi.unstubAllGlobals();
    });

    test("navigates to the fail page when login fails", async () => {
        vi.stubGlobal("fetch", vi.fn().mockResolvedValue({ ok: false, json: async () => ({}) }));
        renderLoginPage();

        submitLogin();

        expect(await screen.findByText("fail page")).toBeInTheDocument();
        expect(screen.queryByText("success page")).not.toBeInTheDocument();
    });

    test("navigates to the success page when login succeeds", async () => {
        vi.stubGlobal("fetch", vi.fn().mockResolvedValue({ ok: true, json: async () => ({ loginTry: true }) }));
        renderLoginPage();

        submitLogin();

        expect(await screen.findByText("success page")).toBeInTheDocument();
    });
});
