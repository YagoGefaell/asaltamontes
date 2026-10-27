import { createContext } from "react";

export const NotificationsContext = createContext({
  unreadCount: 0,
  items: [],
  hasMore: false,
  loading: false,
  error: null,
  loadMore: async () => {},
  refresh: async () => {},
  markRead: async () => {},
  markAllRead: async () => {},
});
