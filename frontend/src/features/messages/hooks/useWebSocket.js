import { useEffect, useRef } from "react";
import { Client } from "@stomp/stompjs";
import { getAccessToken } from "../../auth/services/auth-token.js";
import { API_CONFIG } from "../../../config/constants.js";

export function useWebSocket(isAuthenticated) {
  const clientRef = useRef(null);

  useEffect(() => {
    // Connect only when auth is confirmed and token exists.
    if (!isAuthenticated) {
      if (clientRef.current) {
        clientRef.current.deactivate();
        clientRef.current = null;
      }
      return;
    }

    const connectWebSocket = () => {
      const token = getAccessToken();
      const backendOrigin = API_CONFIG.BASE_URL.replace(/\/$/, "");
      const wsOrigin = backendOrigin.replace(/^http/i, "ws");
      const brokerURL = `${wsOrigin}/ws-asaltamontes`;

      if (!token) {
        console.warn("No access token available for WebSocket connection");
        return;
      }

      const client = new Client({
        brokerURL,
        connectHeaders: {
          Authorization: `Bearer ${token}`,
        },
        heartbeatIncoming: 4000,
        heartbeatOutgoing: 4000,
        reconnectDelay: 5000,
        debug: (msg) => console.log("[WebSocket]", msg),
        onConnect: () => {
          console.log("✓ WebSocket connected successfully", brokerURL);
        },
        onStompError: (frame) => {
          console.error("[WebSocket Error]", frame.headers["message"], frame.body);
        },
        onWebSocketError: (event) => {
          console.error("[WebSocket Connection Error]", event);
        },
        onWebSocketClose: (event) => {
          console.warn("[WebSocket Closed]", event.code, event.reason || "sin motivo");
        },
      });

      clientRef.current = client;
      client.activate();
    };

    connectWebSocket();

    // Cleanup: disconnect on unmount or auth change
    return () => {
      if (clientRef.current) {
        clientRef.current.deactivate();
        clientRef.current = null;
        console.log("✓ WebSocket disconnected");
      }
    };
  }, [isAuthenticated]);
}
