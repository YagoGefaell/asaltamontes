import { createContext } from "react";

export const AuthContext = createContext({
  isAuthenticated: false,
  checkingAuth: true,
  currentUser: null,
  login: async () => {},
  logout: async () => {},
  register: async () => {},
  setCurrentUser: () => {},
  incrementFollowing: () => {},
  decrementFollowing: () => {},
  incrementFollowers: () => {},
  decrementFollowers: () => {},
  sendMessage: () => false,
  websocketClient: null,
  websocketConnected: false,
});
