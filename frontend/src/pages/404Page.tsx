import React from "react";
import Header from "../components/Header";
import Footer from "../components/Footer";

const HomePage: React.FC = () => {
    return (
        <div className="wrapper">
            <Header />
            <article className="contents">
                <div className="area-cmn-01">
                    <section className="sec-cmn-01">
                        <h1 className="sec-cmn-in-01">Error Code: 404</h1>
                        <a href="./index.html">Back to top page</a>
                    </section>
                </div>
            </article>
            <Footer />
        </div>
    );
};

export default HomePage;
