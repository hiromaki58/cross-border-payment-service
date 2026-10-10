import React from "react";
import { BrowserRouter as Router, Routes, Route } from "react-router-dom";
import { AuthProvider } from "./context/AuthContext";
import IndexPage from "./pages/IndexPage";
import LoginPage from "./pages/login/LoginPage";
import LoginSuccessPage from "./pages/login/LoginSuccess";
import LoginFailPage from "./pages/login/LoginFail";
import PasswordResetEmailFormPage from "./pages/login/PasswordResetEmailForm";
import PasswordResetPage from "./pages/login/PasswordReset";
import PasswordResetSuccessPage from "./pages/login/PasswordResetSuccess";
import PasswordResetFail from "./pages/login/PasswordResetFail";
import NotFoundPage from "./pages/404Page";

const App: React.FC = () => {
	return (
		<AuthProvider>
			<Router>
				<Routes>
					<Route path="/" element={<IndexPage />} />
					<Route path="/login" element={<LoginPage />} />
					<Route path="/login/success" element={<LoginSuccessPage />} />
					<Route path="/login/fail" element={<LoginFailPage />} />
					<Route path="/login/password-reset-email-check" element={<PasswordResetEmailFormPage />} />
					<Route path="/login/password-reset-email-check/completed" element={<PasswordResetEmailFormPage />} />
					<Route path="/login/password-reset" element={<PasswordResetPage />} />
					<Route path="/login/password-reset/success" element={<PasswordResetSuccessPage />} />
					<Route path="/login/password-reset/fail" element={<PasswordResetFail />} />
					<Route path="*" element={<NotFoundPage />} />
				</Routes>
			</Router>
		</AuthProvider>
	);
};

export default App;
