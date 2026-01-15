import { createContext } from "react";

export const AuthContext = createContext({
  isAuthenticated: false,
  checkingAuth: true,
  login: async () => {},
  logout: async () => {},
  register: async () => {},
});
