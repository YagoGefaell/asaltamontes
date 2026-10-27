import { useContext } from "react";
import { NotificationsContext } from "./notifications.context";

export function useNotifications() {
  return useContext(NotificationsContext);
}
