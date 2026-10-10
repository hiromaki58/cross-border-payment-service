import React from "react";
import Header from "../../components/Header";
import Footer from "../../components/Footer";
import "../../css/base-pc.css";
import "../../css/login.css";
import { Link } from "react-router-dom";

const PasswordResetEmailFormCompleted: React.FC = () => {
    return(
        <div className="wrapper">
            <Header />
            <article className="contents">
                <div className="area-reset">
                    <section>
                        <h2 className="sec-cmn-in-01">Password reset link is sent.</h2>

                        <div className="form-reset-in">
                            <p className="form-reset-ttl">Please check your E-mail account.</p>
                        </div>

                        <div className="btn-registration-password">
                            <Link to="/login">
                                <span className="btn-registration-password-01">To login page</span>
                            </Link>
                        </div>
                    </section>
                </div>
            </article>
            <Footer />
        </div>
    );
};

export default PasswordResetEmailFormCompleted;