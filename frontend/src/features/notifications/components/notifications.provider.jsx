import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useAuth } from "../../auth/hooks/useAuth";
import { mergeNotifications } from "../utils/mergeNotifications.js";
import { NotificationsContext } from "../hooks/notifications.context";
import {
  getNotificationsRequest,
  getUnreadCountRequest,
  markAllNotificationsReadRequest,
  markNotificationsReadRequest,
} from "../services/notifications.service.js";

// stompjs throws if the connection is already gone (and then the server has dropped the subscription anyway)
const safeUnsubscribe = (subscription) => {
  try {
    subscription?.unsubscribe();
  } catch {
    // Connection closed: nothing to unsubscribe from
  }
};

/**
 * Notifications of the logged-in user (T18): the unread counter and the list, kept up to date over WebSocket.
 * What arrived while the socket was down is not replayed, so every (re)connection asks again for the counter and
 * the first page (T11).
 */
export const NotificationsProvider = ({ children }) => {
  const { websocketClient, websocketConnected, isAuthenticated } = useAuth();

  const [unreadCount, setUnreadCount] = useState(0);
  const [items, setItems] = useState([]);
  const [paging, setPaging] = useState({ hasMore: false, nextBefore: null });
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState(null);

  // A response that comes back after a newer refresh started is stale and is dropped
  const refreshSeq = useRef(0);
  const pagingRef = useRef(paging);
  useEffect(() => {
    pagingRef.current = paging;
  }, [paging]);

  const refresh = useCallback(async () => {
    const seq = ++refreshSeq.current;
    setLoading(true);
    try {
      const [count, page] = await Promise.all([getUnreadCountRequest(), getNotificationsRequest()]);
      if (seq !== refreshSeq.current) return;
      setUnreadCount(count);
      setItems(page.items);
      setPaging({ hasMore: page.hasMore, nextBefore: page.nextBefore });
      setError(null);
    } catch (e) {
      if (seq === refreshSeq.current) setError(e.message || "No se han podido cargar las notificaciones");
    } finally {
      if (seq === refreshSeq.current) setLoading(false);
    }
  }, []);

  const loadMore = useCallback(async () => {
    const { hasMore, nextBefore } = pagingRef.current;
    if (!hasMore || !nextBefore) return;
    const seq = refreshSeq.current;
    setLoading(true);
    try {
      const page = await getNotificationsRequest({ before: nextBefore });
      if (seq !== refreshSeq.current) return;
      setItems((current) => mergeNotifications(current, page.items));
      setPaging({ hasMore: page.hasMore, nextBefore: page.nextBefore });
      setError(null);
    } catch (e) {
      setError(e.message || "No se han podido cargar más notificaciones");
    } finally {
      setLoading(false);
    }
  }, []);

  // Logged in (or out): load or forget
  useEffect(() => {
    if (!isAuthenticated) {
      refreshSeq.current += 1;
      setItems([]);
      setUnreadCount(0);
      setPaging({ hasMore: false, nextBefore: null });
      setError(null);
      setLoading(false);
      return;
    }
    refresh();
  }, [isAuthenticated, refresh]);

  const handleIncoming = useCallback((notification) => {
    if (!notification?.id) return;
    setItems((current) => (current.some((item) => item.id === notification.id) ? current : [notification, ...current]));
    if (!notification.readAt) {
      // Only counted once: a repeated frame for a notification we already hold does not add up
      setUnreadCount((count) => count + 1);
    }
  }, []);

  const itemsRef = useRef(items);
  useEffect(() => {
    itemsRef.current = items;
  }, [items]);

  useEffect(() => {
    if (!websocketClient || !websocketConnected) return undefined;

    const subscription = websocketClient.subscribe("/user/queue/notifications", (frame) => {
      try {
        const notification = JSON.parse(frame.body);
        // Counting needs to know whether we already hold it
        if (itemsRef.current.some((item) => item.id === notification?.id)) return;
        handleIncoming(notification);
      } catch (parseError) {
        console.error("Error parseando notificación websocket", parseError);
      }
    });
    // Whatever arrived while we were not connected
    refresh();

    return () => safeUnsubscribe(subscription);
  }, [websocketClient, websocketConnected, handleIncoming, refresh]);

  const markRead = useCallback(async (notification) => {
    if (!notification || notification.readAt) return;
    const readAt = new Date().toISOString();
    setItems((current) => current.map((item) => (item.id === notification.id ? { ...item, readAt } : item)));
    setUnreadCount((count) => Math.max(0, count - 1));
    try {
      await markNotificationsReadRequest([notification.id]);
    } catch {
      // The server is the truth: put the real counter and list back
      refresh();
    }
  }, [refresh]);

  const markAllRead = useCallback(async () => {
    const readAt = new Date().toISOString();
    setItems((current) => current.map((item) => (item.readAt ? item : { ...item, readAt })));
    setUnreadCount(0);
    try {
      await markAllNotificationsReadRequest();
    } catch {
      refresh();
    }
  }, [refresh]);

  const value = useMemo(() => ({
    unreadCount,
    items,
    hasMore: paging.hasMore,
    loading,
    error,
    loadMore,
    refresh,
    markRead,
    markAllRead,
  }), [unreadCount, items, paging.hasMore, loading, error, loadMore, refresh, markRead, markAllRead]);

  return <NotificationsContext.Provider value={value}>{children}</NotificationsContext.Provider>;
};
