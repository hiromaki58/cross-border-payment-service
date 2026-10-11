import React from "react";
import { BrowserRouter as Router, Routes, Route } from "react-router-dom";
import { AuthProvider } from "./context/AuthContext";
import IndexPage from "./pages/IndexPage";
import LoginPage from "./pages/login/LoginPage";
import LoginSuccessPage from "./pages/login/LoginSuccess";
import LoginFailPage from "./pages/login/LoginFail";
import NotFoundPage from "./pages/404Page";
import ResultPageGuard from "./components/ResultPageGuard";

const App: React.FC = () => {
	return (
		<AuthProvider>
			<Router>
				<Routes>
					<Route path="/" element={<IndexPage />} />
					<Route path="/login" element={<LoginPage />} />
					<Route path="/login/success" element={<ResultPageGuard><LoginSuccessPage /></ResultPageGuard>} />
					<Route path="/login/fail" element={<ResultPageGuard><LoginFailPage /></ResultPageGuard>} />
					<Route path="*" element={<NotFoundPage />} />
				</Routes>
			</Router>
		</AuthProvider>
	);
};

export default App;
