export type AuthContextType = {
    isLoggedIn: boolean;
    login: () => void;
    logout: () => Promise<void>;
};
