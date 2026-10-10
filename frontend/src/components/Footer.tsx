import React from "react";
import "../css/base-pc.css";

const Footer: React.FC = () => {
    return (
        <footer className="footer">
            <section>
                <h2 className="hidden">footer</h2>
                <div className="footer-in">
                    <div className="footer-utility">
                        <div className="footer-utility-in">
                            <div className="footer-logo">
                                <img className="footer-logo-in" src="/img/logo_institution_01.jpg" alt="https://" />
                            </div>
                            <section>
                                <div className="sec-footer-info">
                                    <div className="box-info-wrap">
                                        <p className="ttl-info">Created by</p>
                                    </div>
                                    <div className="box-mail-wrap">
                                        <span className="txt-mail">Hiroyuki Maki</span>
                                    </div>
                                </div>
                            </section>
                            <section>
                                <div className="sec-footer-info">
                                    <div className="box-info-wrap">
                                        <p className="ttl-info">Contact</p>
                                    </div>
                                    <div className="box-mail-wrap">
                                        <span className="txt-mail">hiromaki58@gmail.com</span>
                                    </div>
                                </div>
                            </section>
                            <section>
                                <div className="box-info-wrap">
                                    <p className="ttl-info">SNS</p>
                                </div>
                                <div className="footer-sns">
                                    <ul>
                                        <li><a href="https://www.linkedin.com/in/hiromaki58/">LinkedIn</a></li>
                                        <li><a href="https://github.com/hiromaki58/cross-border-payment-service">Github</a></li>
                                    </ul>
                                </div>
                            </section>
                        </div>
                    </div>
                    <p className="footer-copyright"> &copy; Hiroyuki Maki. All Rights Reserved.</p>
                </div>
            </section>
        </footer>
    );
};

export default Footer;
