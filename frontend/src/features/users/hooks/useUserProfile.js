import { useContext } from "react";
import { UserProfileContext } from "./user.context";

export function useUserProfile() {
  return useContext(UserProfileContext);
}
