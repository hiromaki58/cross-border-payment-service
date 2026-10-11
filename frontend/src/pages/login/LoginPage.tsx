import React from "react";
import Header from "../../components/Header";
import Footer from "../../components/Footer";
import "../../css/base-pc.css";
import "../../css/login.css"

// Password login has been removed.
// Google login and one-time e-mail code login will be added here.
const LoginPage: React.FC = () => {
    return (
        <div className="wrapper">
            <Header />
            <article className="contents">
                <div className="area-cmn-01">
                    <section className="sec-cmn-01">
                        <h1 className="sec-cmn-in-01">Login</h1>
                        <p className="sec-cmn-01-detail">Login with Google or a one-time code sent by e-mail is coming soon.</p>
                    </section>
                </div>
            </article>
            <Footer />
        </div>
    );
};

export default LoginPage;
