import { useEffect, useRef } from "react";

export function useGlobalMessageListener(websocketClient, websocketConnected, onMessageReceived) {
  const subscriptionRef = useRef(null);
  
  const savedCallback = useRef(onMessageReceived);

  useEffect(() => {
    savedCallback.current = onMessageReceived;
  }, [onMessageReceived]);

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

    return () => {
      if (subscriptionRef.current) {
        subscriptionRef.current.unsubscribe();
        subscriptionRef.current = null;
      }
    };
  }, [websocketClient, websocketConnected]); 
}