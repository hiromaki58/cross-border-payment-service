import React from "react";
import { Link } from "react-router-dom";
import Header from "../../components/Header";
import Footer from "../../components/Footer";
import "../../css/base-pc.css";
import "../../css/login.css";

const RegistrationSuccess: React.FC = () => {
    return (
        <div className="wrapper">
            <Header />
            <article className="contents">
                <div className="area-cmn-01">
                    <section className="sec-cmn-01">
                        <h1 className="sec-cmn-in-01">Registration Succeeded</h1>
                        <p className="sec-cmn-01-detail">Please log in with your new passkey.</p>
                        <Link to="/login">Go to login page</Link>
                    </section>
                </div>
            </article>
            <Footer />
        </div>
    );
};

export default RegistrationSuccess;
