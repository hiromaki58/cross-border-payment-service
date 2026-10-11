export type AuthContextType = {
    isLoggedIn: boolean;
    login: () => void;
    logout: () => void;
};
