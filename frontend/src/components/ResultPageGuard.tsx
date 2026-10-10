import React from "react";
import { Navigate, useLocation } from "react-router-dom";
import { useAuthProvider } from "../context/AuthContext";

// State passed with navigate() when moving to a result page from inside the app.
export const RESULT_PAGE_STATE = { fromApp: true } as const;

type Props = {
    children: React.ReactElement;
};

// Result pages (e.g. /login/success) are only shown after an in-app transition.
// Direct access (typed URL, bookmark) is redirected to the top page when logged in,
// otherwise to the login page.
// This only controls page flow; authorization must be enforced by the backend.
const ResultPageGuard: React.FC<Props> = ({ children }) => {
    const location = useLocation();
    const { isLoggedIn } = useAuthProvider();
    const state = location.state as { fromApp?: unknown } | null;

    if (state?.fromApp === true) {
        return children;
    }
    return <Navigate to={isLoggedIn ? "/" : "/login"} replace />;
};

export default ResultPageGuard;
