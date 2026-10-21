import { useEffect, useRef, useState, useCallback } from "react";
import { Client } from "@stomp/stompjs";
import { getAccessToken, isTokenExpiringSoon } from "../../auth/services/auth-token.js";
import { apiClient } from "../../../lib/api-client.js";
import { API_CONFIG } from "../../../config/constants.js";
import { reconnectDelay } from "../utils/reconnectDelay.js";

/** ws(s):// URL of the STOMP endpoint: on the API origin if one is configured, otherwise on this page's origin. */
export function buildBrokerUrl(apiBaseUrl, location = window.location) {
  const origin = apiBaseUrl ? apiBaseUrl.replace(/\/$/, "") : `${location.protocol}//${location.host}`;
  return `${origin.replace(/^http/i, "ws")}/ws-asaltamontes`;
}

/**
 * STOMP connection of the logged-in user. Reconnection is handled here, not by stompjs (T11): before each
 * attempt the access token is renewed if it is about to expire, a CONNECT rejected by the server forces a
 * refresh (and a second rejection ends the session), attempts back off from 1 s to 30 s with jitter, and
 * coming back to the app or to the network reconnects at once.
 *
 * status: "disconnected" (logged out) · "connecting" · "connected" · "reconnecting" · "offline".
 */
export function useWebSocket(isAuthenticated) {
  const clientRef = useRef(null);
  const [client, setClient] = useState(null);
  const [isConnected, setIsConnected] = useState(false);
  // "connecting" until the first attempt ends; "disconnected" is derived from isAuthenticated
  const [status, setStatus] = useState("connecting");

  useEffect(() => {
    if (!isAuthenticated) return;

    let stopped = false;
    let connecting = false;
    let attempt = 0;
    let retryTimer = null;
    let forceRefresh = false;
    // Consecutive CONNECTs rejected by the server
    let authFailures = 0;

    const stompClient = new Client({
      brokerURL: buildBrokerUrl(API_CONFIG.BASE_URL),
      heartbeatIncoming: 4000,
      heartbeatOutgoing: 4000,
      connectionTimeout: 10000,
      // No automatic reconnection from stompjs: see scheduleReconnect
      reconnectDelay: 0,
      // debug: (msg) => console.log("[WebSocket]", msg),

      beforeConnect: async () => {
        connecting = true;
        let token = getAccessToken();
        if ((forceRefresh || isTokenExpiringSoon(token)) && !navigator.onLine) {
          // A refresh without network would fail and end the session: wait for the "online" event
          connecting = false;
          setStatus("offline");
          await stompClient.deactivate();
          return;
        }
        if (forceRefresh || isTokenExpiringSoon(token)) {
          forceRefresh = false;
          // Logs out by itself if the session can no longer be renewed
          token = (await apiClient.refreshSession()) ? getAccessToken() : null;
        }

        if (!token || stopped) {
          // stompjs does not open the socket once the client is inactive
          connecting = false;
          await stompClient.deactivate();
          return;
        }

        stompClient.connectHeaders = {
          Authorization: `Bearer ${token}`,
        };
      },

      onConnect: () => {
        connecting = false;
        attempt = 0;
        authFailures = 0;
        setClient(stompClient);
        setIsConnected(true);
        setStatus("connected");
        console.log("✓ WebSocket connected successfully");
      },
      onStompError: (frame) => {
        console.error("[WebSocket Error]", frame.headers["message"], frame.body);
        // An ERROR before CONNECTED is the server rejecting the token (T09a)
        if (!stompClient.connected) {
          authFailures += 1;
          if (authFailures >= 2) {
            stopped = true;
            apiClient.expireSession();
          } else {
            forceRefresh = true;
          }
        }
      },
      onWebSocketError: () => {
        setIsConnected(false);
        console.error("[WebSocket Connection Error]");
      },
      onWebSocketClose: (event) => {
        connecting = false;
        setClient(null);
        setIsConnected(false);
        console.warn("[WebSocket Closed]", event.code, event.reason || "sin motivo");
        if (!stopped) scheduleReconnect();
      },
    });

    const reconnectNow = async () => {
      clearTimeout(retryTimer);
      retryTimer = null;
      if (stopped || connecting || stompClient.connected) return;
      // Resets the stompjs state (it stays "active" after a close when it does not reconnect by itself)
      await stompClient.deactivate();
      if (!stopped) stompClient.activate();
    };

    const scheduleReconnect = () => {
      clearTimeout(retryTimer);
      if (!navigator.onLine) {
        // The "online" event resumes
        setStatus("offline");
        return;
      }
      setStatus("reconnecting");
      retryTimer = setTimeout(reconnectNow, reconnectDelay(attempt));
      attempt += 1;
    };

    const reconnectSoon = () => {
      if (stopped || stompClient.connected) return;
      attempt = 0;
      reconnectNow();
    };
    const onVisibilityChange = () => {
      if (document.visibilityState === "visible") reconnectSoon();
    };
    const onOffline = () => {
      if (!stompClient.connected) setStatus("offline");
    };

    window.addEventListener("online", reconnectSoon);
    window.addEventListener("offline", onOffline);
    document.addEventListener("visibilitychange", onVisibilityChange);

    clientRef.current = stompClient;
    stompClient.activate();

    return () => {
      stopped = true;
      clearTimeout(retryTimer);
      window.removeEventListener("online", reconnectSoon);
      window.removeEventListener("offline", onOffline);
      document.removeEventListener("visibilitychange", onVisibilityChange);
      stompClient.deactivate();
      clientRef.current = null;
      setClient(null);
      setIsConnected(false);
      setStatus("connecting");
      console.log("✓ WebSocket disconnected");
    };
  }, [isAuthenticated]);

  const sendMessage = useCallback((destination, body) => {
    if (clientRef.current && clientRef.current.connected) {
      clientRef.current.publish({
        destination,
        body: JSON.stringify(body),
      });
      return true;
    } else {
      console.warn("No se pudo enviar el mensaje: WebSocket no conectado");
      return false;
    }
  }, []);

  return {
    websocketClient: client,
    websocketConnected: isConnected,
    websocketStatus: isAuthenticated ? status : "disconnected",
    sendMessage,
  };
}