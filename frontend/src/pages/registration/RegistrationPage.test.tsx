import React from "react";
import { fireEvent, render, screen } from "@testing-library/react";
import { MemoryRouter, Route, Routes } from "react-router-dom";
import { AuthProvider } from "../../context/AuthContext";
import RegistrationPage from "./RegistrationPage";
import { requestRegistration, verifyEmail } from "../../cmn/Registration";
import { registerPasskey } from "../../cmn/Passkey";

vi.mock("../../cmn/Registration", async (importOriginal) => ({
    ...(await importOriginal<typeof import("../../cmn/Registration")>()),
    requestRegistration: vi.fn(),
    verifyEmail: vi.fn(),
}));
vi.mock("../../cmn/Passkey", async (importOriginal) => ({
    ...(await importOriginal<typeof import("../../cmn/Passkey")>()),
    registerPasskey: vi.fn(),
}));

const requestMock = vi.mocked(requestRegistration);
const verifyMock = vi.mocked(verifyEmail);
const registerMock = vi.mocked(registerPasskey);

const renderPage = () =>
    render(
        <AuthProvider>
            <MemoryRouter initialEntries={["/registration"]}>
                <Routes>
                    <Route path="/registration" element={<RegistrationPage />} />
                    <Route path="/registration/success" element={<p>success page</p>} />
                    <Route path="/registration/fail" element={<p>fail page</p>} />
                </Routes>
            </MemoryRouter>
        </AuthProvider>
    );

const type = (label: string, value: string) => fireEvent.change(screen.getByLabelText(label), { target: { value } });

const fillInformation = () => {
    type("First name", "Taro");
    type("Family name", "Yamada");
    type("E-mail address", " taro@example.com ");
};

const goToVerification = async () => {
    requestMock.mockResolvedValue("accepted");
    fillInformation();
    fireEvent.click(screen.getByRole("button", { name: "Send verification code" }));
    await screen.findByRole("heading", { name: "E-mail verification" });
};

const goToPasskey = async () => {
    await goToVerification();
    verifyMock.mockResolvedValue("verified");
    type("Verification code", "12345678");
    fireEvent.click(screen.getByRole("button", { name: "Verify" }));
    await screen.findByRole("heading", { name: "Passkey" });
};

describe("RegistrationPage", () => {
    beforeEach(() => {
        requestMock.mockReset();
        verifyMock.mockReset();
        registerMock.mockReset();
    });

    test("shows the information form without password inputs", () => {
        const { container } = renderPage();

        expect(screen.getByRole("heading", { name: "Registration" })).toBeInTheDocument();
        expect(screen.getByLabelText("First name")).toBeInTheDocument();
        expect(screen.getByLabelText("Family name")).toBeInTheDocument();
        expect(screen.getByLabelText("E-mail address")).toBeInTheDocument();
        expect(container.querySelector('input[type="password"]')).toBeNull();
    });

    test("does not call the API when required input is missing", async () => {
        renderPage();
        type("First name", "Taro");

        fireEvent.click(screen.getByRole("button", { name: "Send verification code" }));

        expect(await screen.findByRole("alert")).toHaveTextContent("Please check the entered information.");
        expect(requestMock).not.toHaveBeenCalled();
    });

    test("rejects control characters in the name before calling the API", async () => {
        renderPage();
        fillInformation();
        type("First name", "Ta\u0007ro");

        fireEvent.click(screen.getByRole("button", { name: "Send verification code" }));

        expect(await screen.findByRole("alert")).toHaveTextContent("Please check the entered information.");
        expect(requestMock).not.toHaveBeenCalled();
    });

    test("shows a message when the server rejects the input", async () => {
        requestMock.mockResolvedValue("invalid");
        renderPage();
        fillInformation();

        fireEvent.click(screen.getByRole("button", { name: "Send verification code" }));

        expect(await screen.findByRole("alert")).toHaveTextContent("Please check the entered information.");
        expect(screen.getByLabelText("First name")).toBeInTheDocument();
    });

    test("sends the trimmed e-mail address and moves to the code step", async () => {
        renderPage();
        await goToVerification();

        expect(requestMock).toHaveBeenCalledWith({ firstName: "Taro", familyName: "Yamada", emailAddr: "taro@example.com" });
        expect(screen.getByLabelText("Verification code")).toBeInTheDocument();
    });

    test("checks the code format before calling the API", async () => {
        renderPage();
        await goToVerification();
        type("Verification code", "1234");

        fireEvent.click(screen.getByRole("button", { name: "Verify" }));

        expect(await screen.findByRole("alert")).toHaveTextContent("Enter the 8-digit code");
        expect(verifyMock).not.toHaveBeenCalled();
    });

    test("shows a message for an invalid code and stays on the code step", async () => {
        renderPage();
        await goToVerification();
        verifyMock.mockResolvedValue("invalid");
        type("Verification code", "12345678");

        fireEvent.click(screen.getByRole("button", { name: "Verify" }));

        expect(await screen.findByRole("alert")).toHaveTextContent("invalid or has expired");
        expect(screen.getByLabelText("Verification code")).toBeInTheDocument();
    });

    test("can go back to the first step with the entered information kept", async () => {
        renderPage();
        await goToVerification();

        fireEvent.click(screen.getByRole("button", { name: /send a new code/ }));

        expect(screen.getByLabelText("First name")).toHaveValue("Taro");
        expect(screen.getByLabelText("E-mail address")).toHaveValue("taro@example.com");
    });

    test("registers the passkey and moves to the success page", async () => {
        renderPage();
        await goToPasskey();
        registerMock.mockResolvedValue("success");
        type("Passkey name", " My phone ");

        fireEvent.click(screen.getByRole("button", { name: "Create passkey" }));

        expect(await screen.findByText("success page")).toBeInTheDocument();
        expect(registerMock).toHaveBeenCalledWith("My phone");
    });

    test("requires a passkey name", async () => {
        renderPage();
        await goToPasskey();
        type("Passkey name", "   ");

        fireEvent.click(screen.getByRole("button", { name: "Create passkey" }));

        expect(await screen.findByRole("alert")).toHaveTextContent("Enter a name");
        expect(registerMock).not.toHaveBeenCalled();
    });

    test.each(["rejected", "expired"] as const)("moves to the failure page when the result is %s", async (result) => {
        renderPage();
        await goToPasskey();
        registerMock.mockResolvedValue(result);

        fireEvent.click(screen.getByRole("button", { name: "Create passkey" }));

        expect(await screen.findByText("fail page")).toBeInTheDocument();
    });

    test("stays on the passkey step when creation is cancelled", async () => {
        renderPage();
        await goToPasskey();
        registerMock.mockResolvedValue("cancelled");

        fireEvent.click(screen.getByRole("button", { name: "Create passkey" }));

        expect(await screen.findByRole("alert")).toHaveTextContent("cancelled");
        expect(screen.getByRole("button", { name: "Create passkey" })).toBeEnabled();
    });
});
