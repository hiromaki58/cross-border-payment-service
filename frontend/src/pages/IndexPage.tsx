import React from "react";
import { Link } from "react-router-dom";
import Header from "../components/Header";
import Footer from "../components/Footer";
import { useAuthProvider } from "../context/AuthContext";
import "../css/base-pc.css";

// Top page. The title follows the title of html/src/index.html (.ttl-game-* styles, renamed .ttl-top-*).
const HomePage: React.FC = () => {
    const { isLoggedIn } = useAuthProvider();

    return (
        <div className="wrapper">
            <Header />
            <div className="contents">
                <div className="area-top">
                    <div className="sec-top">
                        <section>
                            <h2 className="ttl-top-h2">Cross-Border Payment Service</h2>
                            {!isLoggedIn && (
                                <>
                                    <h3 className="ttl-top-h3">Log in with a passkey to send money abroad</h3>
                                    <div className="box-top-login">
                                        <Link className="btn-top-login" to="/login">Go to login page</Link>
                                    </div>
                                </>
                            )}
                        </section>
                    </div>
                </div>
            </div>
            <Footer />
        </div>
    );
};

export default HomePage;
