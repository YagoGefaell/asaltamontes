import { createContext} from "react";

export const UserProfileContext = createContext({
    userProfile: null,
    setUserProfile: () => {},
    loading: true,
});