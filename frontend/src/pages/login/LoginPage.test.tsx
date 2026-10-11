import React from "react";
import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { AuthProvider } from "../../context/AuthContext";
import LoginPage from "./LoginPage";
import { loginWithPasskey } from "../../cmn/Passkey";

vi.mock("../../cmn/Passkey", () => ({
    loginWithPasskey: vi.fn(),
}));

const loginMock = vi.mocked(loginWithPasskey);

const renderPage = () =>
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

const clickLogin = () => fireEvent.click(screen.getByRole("button", { name: "Login with passkey" }));

describe("LoginPage", () => {
    beforeEach(() => {
        loginMock.mockReset();
        sessionStorage.clear();
    });

    test("shows the passkey login form without a password input", () => {
        const { container } = renderPage();

        expect(screen.getByRole("heading", { name: "Login" })).toBeInTheDocument();
        expect(screen.getByRole("button", { name: "Login with passkey" })).toBeInTheDocument();
        expect(container.querySelector('input[type="password"]')).toBeNull();
        expect(screen.queryByText(/forget password/i)).not.toBeInTheDocument();
        expect(loginMock).not.toHaveBeenCalled();
    });

    test("moves to the success page and stores the login state after a successful login", async () => {
        loginMock.mockResolvedValue("success");
        renderPage();

        clickLogin();

        expect(await screen.findByText("success page")).toBeInTheDocument();
        expect(sessionStorage.getItem("isLoggedIn")).toBe("true");
    });

    test("moves to the failure page when the server rejects the passkey", async () => {
        loginMock.mockResolvedValue("rejected");
        renderPage();

        clickLogin();

        expect(await screen.findByText("fail page")).toBeInTheDocument();
        expect(sessionStorage.getItem("isLoggedIn")).toBeNull();
    });

    test.each([
        ["cancelled", /cancelled/],
        ["unsupported", /does not support passkeys/],
        ["error", /not available right now/],
    ] as const)("stays on the page with a message when the result is %s", async (result, text) => {
        loginMock.mockResolvedValue(result);
        renderPage();

        clickLogin();

        expect(await screen.findByRole("alert")).toHaveTextContent(text);
        expect(screen.getByRole("button", { name: "Login with passkey" })).toBeEnabled();
        expect(sessionStorage.getItem("isLoggedIn")).toBeNull();
    });

    test("does not start a second login while one is in progress", async () => {
        let resolve: (value: "cancelled") => void = () => {};
        loginMock.mockReturnValue(new Promise((r) => { resolve = r; }));
        renderPage();

        clickLogin();
        const button = await screen.findByRole("button", { name: "Logging in..." });
        expect(button).toBeDisabled();
        fireEvent.submit(button.closest("form") as HTMLFormElement);

        expect(loginMock).toHaveBeenCalledTimes(1);
        resolve("cancelled");
        await waitFor(() => expect(screen.getByRole("button", { name: "Login with passkey" })).toBeEnabled());
    });
});
