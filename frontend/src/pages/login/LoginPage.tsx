import React, { useState } from "react";
import { useNavigate } from "react-router-dom";
import Header from "../../components/Header";
import Footer from "../../components/Footer";
import { RESULT_PAGE_STATE } from "../../components/ResultPageGuard";
import { useAuthProvider } from "../../context/AuthContext";
import { loginWithPasskey } from "../../cmn/Passkey";
import "../../css/base-pc.css";
import "../../css/login.css"

// Messages shown on this page when the login did not complete (fixed wording, no server details)
const MESSAGES = {
    cancelled: "Login was cancelled. Please try again.",
    unsupported: "This browser does not support passkeys. Please use a browser that supports passkeys.",
    error: "Login is not available right now. Please try again later.",
} as const;

// Passkey login (the only login method).
const LoginPage: React.FC = () => {
    const { login } = useAuthProvider();
    const navigate = useNavigate();
    const [inProgress, setInProgress] = useState(false);
    const [message, setMessage] = useState<string | null>(null);

    const handleSubmit = async (event: React.FormEvent<HTMLFormElement>) => {
        event.preventDefault();
        if (inProgress) {
            return;
        }
        setInProgress(true);
        setMessage(null);

        const result = await loginWithPasskey();
        if (result === "success") {
            login();
            navigate("/login/success", { state: RESULT_PAGE_STATE });
            return;
        }
        if (result === "rejected") {
            navigate("/login/fail", { state: RESULT_PAGE_STATE });
            return;
        }
        setMessage(MESSAGES[result]);
        setInProgress(false);
    };

    return (
        <div className="wrapper">
            <Header />
            <article className="contents">
                <div className="area-cmn-01">
                    <section className="sec-cmn-01">
                        <h1 className="sec-cmn-in-01">Login</h1>

                        <form className="form-login" onSubmit={handleSubmit}>
                            <div className="form-login-in">
                                <p className="form-login-ttl">Log in with the passkey saved on your device.</p>
                                {message && <p className="form-login-ttl" role="alert">{message}</p>}
                            </div>

                            <button className="form-login-button form-login-button-passkey" type="submit" disabled={inProgress}>
                                {inProgress ? "Logging in..." : "Login with passkey"}
                            </button>
                        </form>
                    </section>
                </div>
            </article>
            <Footer />
        </div>
    );
};

export default LoginPage;
