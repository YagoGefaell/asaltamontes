import { useEffect, useRef } from "react";

const logChatError = (error) => console.warn("Error del chat:", error?.detail);

// stompjs throws if the connection is already gone (and then the server has dropped the subscription anyway)
const safeUnsubscribe = (subscription) => {
  try {
    subscription?.unsubscribe();
  } catch {
    // Connection closed: nothing to unsubscribe from
  }
};

// onMessageDeleted ({ conversationId, messageId }, T20d): a moderator deleted a message of a community chat
// onConversationRemoved ({ conversationId, reason: "REMOVED" | "LEFT" }, T12): a chat has to leave the list
export function useGlobalMessageListener(websocketClient, websocketConnected, onMessageReceived, onError = logChatError, onConversationRemoved = null, onMessageDeleted = null) {
  const subscriptionRef = useRef(null);
  const errorSubscriptionRef = useRef(null);
  const removedSubscriptionRef = useRef(null);
  const deletedSubscriptionRef = useRef(null);
  
  const savedCallback = useRef(onMessageReceived);
  const savedErrorCallback = useRef(onError);
  const savedRemovedCallback = useRef(onConversationRemoved);
  const savedDeletedCallback = useRef(onMessageDeleted);

  useEffect(() => {
    savedCallback.current = onMessageReceived;
  }, [onMessageReceived]);

  useEffect(() => {
    savedErrorCallback.current = onError;
  }, [onError]);

  useEffect(() => {
    savedRemovedCallback.current = onConversationRemoved;
  }, [onConversationRemoved]);

  useEffect(() => {
    savedDeletedCallback.current = onMessageDeleted;
  }, [onMessageDeleted]);

  useEffect(() => {
    if (!websocketClient || !websocketConnected) {
      return;
    }

    safeUnsubscribe(subscriptionRef.current);
    subscriptionRef.current = null;

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

    removedSubscriptionRef.current = websocketClient.subscribe("/user/queue/conversations", (frame) => {
      try {
        savedRemovedCallback.current?.(JSON.parse(frame.body));
      } catch (parseError) {
        console.error("Error parseando evento de conversación", parseError);
      }
    });

    deletedSubscriptionRef.current = websocketClient.subscribe("/user/queue/message-deletions", (frame) => {
      try {
        savedDeletedCallback.current?.(JSON.parse(frame.body));
      } catch (parseError) {
        console.error("Error parseando borrado de mensaje", parseError);
      }
    });

    return () => {
      safeUnsubscribe(deletedSubscriptionRef.current);
      deletedSubscriptionRef.current = null;
      safeUnsubscribe(removedSubscriptionRef.current);
      removedSubscriptionRef.current = null;
      safeUnsubscribe(subscriptionRef.current);
      subscriptionRef.current = null;
      safeUnsubscribe(errorSubscriptionRef.current);
      errorSubscriptionRef.current = null;
    };
  }, [websocketClient, websocketConnected]); 
}