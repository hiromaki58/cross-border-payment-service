import React, { useState } from "react";
import Header from "../../components/Header";
import Footer from "../../components/Footer";
import "../../css/base-pc.css";
import "../../css/login.css";
import { useNavigate } from "react-router-dom";
import { API_BASE_URL } from "../../cmn/Constant";

const PasswordResetEmailForm: React.FC = () => {
    const navigate = useNavigate();
    const [msg, setMsg] = useState("");
    const [emailAddr, setEmailAddr] = useState("");

    const handleSubmit = async (e: React.FormEvent) => {
        e.preventDefault();

        try{
            await fetch(`${API_BASE_URL}/api/player/password-reset-email-check`, {
                method: "PUT",
                headers: { "Content-Type": "application/json" },
                credentials: "include",
                body: JSON.stringify({ emailAddr }),
            });
            navigate("/login/password-reset-email-check/completed");
        }
        catch(err){
            setMsg("Please try again later.");
        }
    }

    return (
        <div className="wrapper">
            <Header />
            <article className="contents">
                <div className="area-cmn-01">
                    <section className="sec-cmn-01">
                        <h1 className="sec-cmn-in-01">Login</h1>
                        <form className="form-login" onSubmit={handleSubmit}>
                            <div className="form-login-in">
                                <div className="form-login-ttl">E-mail address</div>
                                <input className="form-login-input" type="email" id="email" value={emailAddr} onChange={(e) => setEmailAddr(e.target.value)} required placeholder="E-mail" /><br />
                            </div>

                            <input className="form-login-button" type="submit" value="login" />
                        </form>
                        {msg && <p>{msg}</p>}
                        <a href="./reissue.html">If you forget password</a>
                    </section>
                </div>
            </article>
            <Footer />
        </div>
    );
};

export default PasswordResetEmailForm;