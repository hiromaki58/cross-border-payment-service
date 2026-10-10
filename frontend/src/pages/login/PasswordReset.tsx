import React, { useEffect, useState } from "react";
import Header from "../../components/Header";
import Footer from "../../components/Footer";
import "../../css/base-pc.css";
import "../../css/login.css";
import { useNavigate } from "react-router-dom";
import { API_BASE_URL } from "../../cmn/Constant";

const PasswordReset: React.FC = () => {
    const navigate = useNavigate();
    const [msg, setMsg] = useState("");
    const [emailAddr, setEmailAddr] = useState("");
    const [newPassword, setNewPassword] = useState("");
    const [newConfirmPassword, setNewConfirmPassword] = useState("");

    useEffect(() => {
        const emailAddrSessionInfo = sessionStorage.getItem("emailAddr");
        if(emailAddrSessionInfo){
            setEmailAddr(emailAddrSessionInfo);
        }
    }, []);

    const handleSubmit = async (e: React.FormEvent) => {
        e.preventDefault();

        if(newPassword !== newConfirmPassword){
            setMsg("The password is not matched.");
            return;
        }

        try{
            const response = await fetch(`${API_BASE_URL}/api/player/password-reset`, {
                method: "PUT",
                headers: { "Content-Type": "application/json" },
                credentials: "include",
                body: JSON.stringify({ newPassword }),
            });

            if(response.ok){
                navigate("/login/password-reset/success");
            }
            else{
                navigate("/login/password-reset/fail");
            }
        }
        catch(err){
            setMsg("Please try again later.");
        }
    }

    return(
        <div className="wrapper">
            <Header />
            <article className="contents">
                <div className="area-cmn-01">
                    <section className="sec-cmn-01">
                        <h1 className="sec-cmn-in-01">Password reset</h1>
                        <div className="form-reset-in">
                            <div className="form-reset-ttl">E-mail address</div>
                            <div className="form-reset-mail">{emailAddr}</div>
                        </div>
                        <form className="form-login" onSubmit={handleSubmit}>
                            <div className="form-login-in">
                                <div className="form-login-ttl">New password</div>
                                <input className="form-login-input" type="password" value={newPassword} onChange={(e) => setNewPassword(e.target.value)} required placeholder="Password" /><br />
                            </div>
                            <div className="form-login-in">
                                <div className="form-login-ttl">New password(Re-enter)</div>
                                <input className="form-login-input" type="password" value={newConfirmPassword} onChange={(e) => setNewConfirmPassword(e.target.value)} required placeholder="Password(Re-enter)" /><br />
                            </div>
                            <input className="form-login-button" type="submit" value="Save" />
                        </form>
                        {msg && <p>{msg}</p>}
                    </section>
                </div>
            </article>
            <Footer />
        </div>
    );
};

export default PasswordReset;