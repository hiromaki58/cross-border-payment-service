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
import RegistrationPage from "./pages/registration/Registration";
import RegistrationSuccessPage from "./pages/registration/RegistrationSuccess";
import RegistrationFailPage from "./pages/registration/RegistrationFail";
import ProfilePage from "./pages/ProfilePage";
import UnsubscribePage from "./pages/registration/Unsubscribe";
import UnsubscribeFailPage from "./pages/registration/UnsubscribeFail";
import NotFoundPage from "./pages/404Page";
import GamePage from "./pages/GamePage";

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
					<Route path="/registration" element={<RegistrationPage />} />
					<Route path="/registration/success" element={<RegistrationSuccessPage />} />
					<Route path="/registration/fail" element={<RegistrationFailPage />} />
					<Route path="/profile" element={<ProfilePage />} />
					<Route path="/unsubscribe" element={<UnsubscribePage />} />
					<Route path="/unsubscribe/fail" element={<UnsubscribeFailPage />} />
					<Route path="/game/minesweeper" element={<GamePage />} />
					<Route path="*" element={<NotFoundPage />} />
				</Routes>
			</Router>
		</AuthProvider>
	);
};

export default App;
