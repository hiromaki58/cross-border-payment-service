import React, { useState } from "react";
import { useNavigate } from "react-router-dom";
import Header from "../../components/Header";
import Footer from "../../components/Footer";
import { RESULT_PAGE_STATE } from "../../components/ResultPageGuard";
import { PASSKEY_LABEL_MAX_LENGTH, registerPasskey } from "../../cmn/Passkey";
import {
    CODE_PATTERN,
    EMAIL_MAX_LENGTH,
    NAME_MAX_LENGTH,
    RegistrationInput,
    requestRegistration,
    verifyEmail,
} from "../../cmn/Registration";
import "../../css/base-pc.css";
import "../../css/registration.css";

// 1. name and e-mail address -> 2. one-time code from the e-mail -> 3. passkey
type Step = "information" | "verification" | "passkey";

// Fixed wording; server details are never shown
const MESSAGES = {
    invalidInput: "Please check the entered information.",
    invalidCode: "The verification code is invalid or has expired.",
    codeFormat: "Enter the 8-digit code from the e-mail.",
    labelFormat: `Enter a name of up to ${PASSKEY_LABEL_MAX_LENGTH} characters.`,
    cancelled: "Passkey creation was cancelled. Please try again.",
    unsupported: "This browser does not support passkeys. Please use a browser that supports passkeys.",
    error: "Registration is not available right now. Please try again later.",
} as const;

// Control characters are rejected by the backend as well
// eslint-disable-next-line no-control-regex
const CONTROL_CHARACTERS = /[\u0000-\u001F\u007F-\u009F]/;

const isValidText = (value: string, maxLength: number): boolean =>
    value.trim().length > 0 && value.length <= maxLength && !CONTROL_CHARACTERS.test(value);

// Registration with e-mail verification and a passkey.
const RegistrationPage: React.FC = () => {
    const navigate = useNavigate();
    const [step, setStep] = useState<Step>("information");
    const [input, setInput] = useState<RegistrationInput>({ firstName: "", familyName: "", emailAddr: "" });
    const [code, setCode] = useState("");
    const [label, setLabel] = useState("My passkey");
    const [inProgress, setInProgress] = useState(false);
    const [message, setMessage] = useState<string | null>(null);

    const handleInputChange = (event: React.ChangeEvent<HTMLInputElement>) => {
        const { name, value } = event.target;
        setInput((current) => ({ ...current, [name]: value }));
    };

    const run = async (action: () => Promise<void>) => {
        if (inProgress) {
            return;
        }
        setInProgress(true);
        setMessage(null);
        try {
            await action();
        } finally {
            setInProgress(false);
        }
    };

    const handleInformationSubmit = (event: React.FormEvent<HTMLFormElement>) => {
        event.preventDefault();
        void run(async () => {
            const trimmed = { ...input, emailAddr: input.emailAddr.trim() };
            if (!isValidText(trimmed.firstName, NAME_MAX_LENGTH) || !isValidText(trimmed.familyName, NAME_MAX_LENGTH)
                || !isValidText(trimmed.emailAddr, EMAIL_MAX_LENGTH)) {
                setMessage(MESSAGES.invalidInput);
                return;
            }
            const result = await requestRegistration(trimmed);
            if (result === "accepted") {
                setCode("");
                setStep("verification");
                return;
            }
            setMessage(result === "invalid" ? MESSAGES.invalidInput : MESSAGES.error);
        });
    };

    const handleVerificationSubmit = (event: React.FormEvent<HTMLFormElement>) => {
        event.preventDefault();
        void run(async () => {
            if (!CODE_PATTERN.test(code)) {
                setMessage(MESSAGES.codeFormat);
                return;
            }
            const result = await verifyEmail(code);
            if (result === "verified") {
                setStep("passkey");
                return;
            }
            setMessage(result === "invalid" ? MESSAGES.invalidCode : MESSAGES.error);
        });
    };

    const handlePasskeySubmit = (event: React.FormEvent<HTMLFormElement>) => {
        event.preventDefault();
        void run(async () => {
            if (!isValidText(label, PASSKEY_LABEL_MAX_LENGTH)) {
                setMessage(MESSAGES.labelFormat);
                return;
            }
            const result = await registerPasskey(label.trim());
            if (result === "success") {
                navigate("/registration/success", { state: RESULT_PAGE_STATE });
                return;
            }
            if (result === "rejected" || result === "expired") {
                navigate("/registration/fail", { state: RESULT_PAGE_STATE });
                return;
            }
            setMessage(MESSAGES[result]);
        });
    };

    // Back to the first step to send a new code (e.g. a typo in the address or no e-mail received)
    const handleRestart = () => {
        setMessage(null);
        setCode("");
        setStep("information");
    };

    const errorMessage = message && <p className="txt-registration-error" role="alert">{message}</p>;

    return (
        <div className="wrapper">
            <Header />
            <article className="contents clearfix">
                <div className="contents-ttl">
                    <section className="sec-ttl">
                        <h1 className="sec-ttl-in">Registration</h1>
                    </section>
                </div>
                <div className="contents-in">
                    {step === "information" && (
                        <form className="form-register-player" onSubmit={handleInformationSubmit} noValidate>
                            <section className="sec-register-player-01">
                                <h2 className="ttl-register-player">Information</h2>
                                <ul className="form-cmn-01-wrap">
                                    <li className="form-cmn-01">
                                        <label className="form-registrtion-ttl-01" htmlFor="firstName">First name</label>
                                        <input className="form-registrtion-input-01" id="firstName" type="text" name="firstName"
                                            placeholder="First name" autoComplete="given-name" maxLength={NAME_MAX_LENGTH}
                                            required value={input.firstName} onChange={handleInputChange} />
                                    </li>
                                    <li className="form-cmn-01">
                                        <label className="form-registrtion-ttl-01" htmlFor="familyName">Family name</label>
                                        <input className="form-registrtion-input-01" id="familyName" type="text" name="familyName"
                                            placeholder="Family name" autoComplete="family-name" maxLength={NAME_MAX_LENGTH}
                                            required value={input.familyName} onChange={handleInputChange} />
                                    </li>
                                </ul>
                                <ul className="form-cmn-01-wrap">
                                    <li className="form-cmn-01">
                                        <label className="form-registrtion-ttl-02" htmlFor="emailAddr">E-mail address</label>
                                        <input className="form-registrtion-input-02" id="emailAddr" type="email" name="emailAddr"
                                            placeholder="E-mail address" autoComplete="email" maxLength={EMAIL_MAX_LENGTH}
                                            required value={input.emailAddr} onChange={handleInputChange} />
                                    </li>
                                </ul>
                                {errorMessage}
                            </section>
                            <div className="btn-registration-player">
                                <button className="btb-cmn-positive-01" type="submit" disabled={inProgress}>
                                    Send verification code
                                </button>
                            </div>
                        </form>
                    )}

                    {step === "verification" && (
                        <form className="form-register-player" onSubmit={handleVerificationSubmit} noValidate>
                            <section className="sec-register-player-01">
                                <h2 className="ttl-register-player">E-mail verification</h2>
                                <p className="txt-registration-detail">
                                    If the e-mail address can be used, a verification code has been sent to it.
                                    Enter the 8-digit code within 10 minutes.
                                </p>
                                <ul className="form-cmn-01-wrap">
                                    <li className="form-cmn-01">
                                        <label className="form-registrtion-ttl-02" htmlFor="code">Verification code</label>
                                        <input className="form-registrtion-input-02" id="code" type="text" name="code"
                                            placeholder="8-digit code" inputMode="numeric" autoComplete="one-time-code"
                                            maxLength={8} required value={code}
                                            onChange={(event) => setCode(event.target.value.trim())} />
                                    </li>
                                </ul>
                                {errorMessage}
                                <p className="txt-registration-detail">
                                    <button className="btn-registration-sub" type="button" onClick={handleRestart} disabled={inProgress}>
                                        Change the e-mail address or send a new code
                                    </button>
                                </p>
                            </section>
                            <div className="btn-registration-player">
                                <button className="btb-cmn-positive-01" type="submit" disabled={inProgress}>Verify</button>
                            </div>
                        </form>
                    )}

                    {step === "passkey" && (
                        <form className="form-register-player" onSubmit={handlePasskeySubmit} noValidate>
                            <section className="sec-register-player-01">
                                <h2 className="ttl-register-player">Passkey</h2>
                                <p className="txt-registration-detail">
                                    Your e-mail address has been verified. Create a passkey on this device to log in.
                                </p>
                                <ul className="form-cmn-01-wrap">
                                    <li className="form-cmn-01">
                                        <label className="form-registrtion-ttl-02" htmlFor="label">Passkey name</label>
                                        <input className="form-registrtion-input-02" id="label" type="text" name="label"
                                            placeholder="e.g. My phone" maxLength={PASSKEY_LABEL_MAX_LENGTH} required
                                            value={label} onChange={(event) => setLabel(event.target.value)} />
                                    </li>
                                </ul>
                                {errorMessage}
                            </section>
                            <div className="btn-registration-player">
                                <button className="btb-cmn-positive-01" type="submit" disabled={inProgress}>
                                    {inProgress ? "Creating..." : "Create passkey"}
                                </button>
                            </div>
                        </form>
                    )}
                </div>
            </article>
            <Footer />
        </div>
    );
};

export default RegistrationPage;
