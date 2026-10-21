import { useEffect, useRef } from "react";

const logChatError = (error) => console.warn("Error del chat:", error?.detail);

export function useGlobalMessageListener(websocketClient, websocketConnected, onMessageReceived, onError = logChatError) {
  const subscriptionRef = useRef(null);
  const errorSubscriptionRef = useRef(null);
  
  const savedCallback = useRef(onMessageReceived);
  const savedErrorCallback = useRef(onError);

  useEffect(() => {
    savedCallback.current = onMessageReceived;
  }, [onMessageReceived]);

  useEffect(() => {
    savedErrorCallback.current = onError;
  }, [onError]);

  useEffect(() => {
    if (!websocketClient || !websocketConnected) {
      return;
    }

    if (subscriptionRef.current) {
      subscriptionRef.current.unsubscribe();
      subscriptionRef.current = null;
    }

    subscriptionRef.current = websocketClient.subscribe("/user/queue/messages", (frame) => {
      try {
        const payload = JSON.parse(frame.body);
        if (savedCallback.current) {
          savedCallback.current(payload);
        }
      } catch (parseError) {
        console.error("Error parseando mensaje websocket", parseError);
      }
    });

    // Errors of our own STOMP sends (e.g. writing to a chat we don't belong to), see ChatController
    errorSubscriptionRef.current = websocketClient.subscribe("/user/queue/errors", (frame) => {
      try {
        savedErrorCallback.current?.(JSON.parse(frame.body));
      } catch (parseError) {
        console.error("Error parseando error websocket", parseError);
      }
    });

    return () => {
      if (subscriptionRef.current) {
        subscriptionRef.current.unsubscribe();
        subscriptionRef.current = null;
      }
      if (errorSubscriptionRef.current) {
        errorSubscriptionRef.current.unsubscribe();
        errorSubscriptionRef.current = null;
      }
    };
  }, [websocketClient, websocketConnected]); 
}