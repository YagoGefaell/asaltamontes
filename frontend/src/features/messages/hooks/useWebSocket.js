import { useEffect, useRef, useState, useCallback } from "react";
import { Client } from "@stomp/stompjs";
import { getAccessToken } from "../../auth/services/auth-token.js";
import { API_CONFIG } from "../../../config/constants.js";

/** ws(s):// URL of the STOMP endpoint: on the API origin if one is configured, otherwise on this page's origin. */
export function buildBrokerUrl(apiBaseUrl, location = window.location) {
  const origin = apiBaseUrl ? apiBaseUrl.replace(/\/$/, "") : `${location.protocol}//${location.host}`;
  return `${origin.replace(/^http/i, "ws")}/ws-asaltamontes`;
}

export function useWebSocket(isAuthenticated) {
  const clientRef = useRef(null);
  const [client, setClient] = useState(null);
  const [isConnected, setIsConnected] = useState(false);

  useEffect(() => {
    if (!isAuthenticated) {
      if (clientRef.current) {
        clientRef.current.deactivate();
        clientRef.current = null;
      }
      return;
    }

    const connectWebSocket = () => {
      const brokerURL = buildBrokerUrl(API_CONFIG.BASE_URL);

      const stompClient = new Client({
        brokerURL,
        heartbeatIncoming: 4000,
        heartbeatOutgoing: 4000,
        reconnectDelay: 5000,
        // debug: (msg) => console.log("[WebSocket]", msg),

        beforeConnect: () => {
          const freshToken = getAccessToken();
          if (!freshToken) {
            throw new Error("No hay token disponible para el handshake websocket");
          }

          stompClient.connectHeaders = {
            Authorization: `Bearer ${freshToken}`,
          };
        },

        onConnect: () => {
          setClient(stompClient);
          setIsConnected(true);
          console.log("✓ WebSocket connected successfully");
        },
        onStompError: (frame) => {
          console.error("[WebSocket Error]", frame.headers["message"], frame.body);
        },
        onWebSocketError: () => {
          setIsConnected(false);
          console.error("[WebSocket Connection Error]");
        },
        onWebSocketClose: (event) => {
          setClient(null);
          setIsConnected(false);
          console.warn("[WebSocket Closed]", event.code, event.reason || "sin motivo");
        },
      });

      clientRef.current = stompClient;
      stompClient.activate();
    };

    connectWebSocket();

    return () => {
      if (clientRef.current) {
        clientRef.current.deactivate();
        clientRef.current = null;
        console.log("✓ WebSocket disconnected");
      }
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
    sendMessage,
  };
}