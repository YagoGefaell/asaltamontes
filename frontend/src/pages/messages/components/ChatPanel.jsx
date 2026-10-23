import { useEffect, useLayoutEffect, useRef } from "react";
import { CHAT } from "../../../config/constants";

// Show the counter only when it matters
const COUNTER_THRESHOLD = CHAT.MAX_MESSAGE_LENGTH - 200;

// Shown above the input while the WebSocket is not connected (T11)
const CONNECTION_NOTICES = {
  connecting: "Conectando…",
  reconnecting: "Reconectando…",
  offline: "Sin conexión. Los mensajes llegarán cuando vuelva la red.",
};

const ChatPane = ({
  selectedChat,
  currentMessages,
  currentUsername,
  draftMessage,
  onDraftChange,
  onSendMessage,
  sendError,
  hasMoreHistory = false,
  loadingOlder = false,
  onLoadOlder,
  historyStatus = "loaded",
  onRetryHistory,
  onLeaveChat,
  onOpenUserProfile,
  onSelectedAvatarError,
  websocketConnected,
  websocketStatus = "connected",
  messagesEndRef,
}) => {
  const containerRef = useRef(null);
  const topSentinelRef = useRef(null);
  // Scroll height before an older page is added, to keep the same messages on screen afterwards
  const heightBeforeLoadRef = useRef(null);
  const oldestMessageId = currentMessages.length > 0 ? currentMessages[0].id : null;

  // Load older messages when the top of the list becomes visible (infinite scroll upwards)
  useEffect(() => {
    const sentinel = topSentinelRef.current;
    if (!sentinel || !hasMoreHistory || !onLoadOlder || typeof IntersectionObserver === "undefined") return;

    const observer = new IntersectionObserver((entries) => {
      if (entries.some((entry) => entry.isIntersecting) && !loadingOlder) {
        heightBeforeLoadRef.current = containerRef.current?.scrollHeight ?? null;
        onLoadOlder();
      }
    }, { root: containerRef.current, rootMargin: "200px 0px 0px 0px" });

    observer.observe(sentinel);
    return () => observer.disconnect();
  }, [hasMoreHistory, loadingOlder, onLoadOlder, oldestMessageId]);

  // After prepending a page, shift the scroll by the added height so nothing jumps
  useLayoutEffect(() => {
    const container = containerRef.current;
    if (!container || heightBeforeLoadRef.current == null) return;
    container.scrollTop += container.scrollHeight - heightBeforeLoadRef.current;
    heightBeforeLoadRef.current = null;
  }, [oldestMessageId]);

  const parseMessageDate = (dateLike) => {
    if (!dateLike) return null;

    const rawValue = String(dateLike).trim();
    const hasTimezone = /[zZ]|[+-]\d{2}:?\d{2}$/.test(rawValue);
    const normalizedValue = hasTimezone ? rawValue : `${rawValue}Z`;
    const parsedDate = new Date(normalizedValue);

    if (Number.isNaN(parsedDate.getTime())) return null;
    return parsedDate;
  };

  const formatMarkerTime = (dateLike) => {
    const date = parseMessageDate(dateLike);
    if (!date) return "";

    return date.toLocaleTimeString(undefined, {
      hour: "2-digit",
      minute: "2-digit",
    });
  };

  const shouldRenderTimeMarker = (messages, index) => {
    if (index === 0) return true;

    const currentDate = parseMessageDate(messages[index]?.sentAt);
    const previousDate = parseMessageDate(messages[index - 1]?.sentAt);

    if (!currentDate || !previousDate) return false;

    const oneHourMs = 60 * 60 * 1000;
    return (currentDate.getTime() - previousDate.getTime()) > oneHourMs;
  };

  return (
    <>
      <div className="messages-header">
        <button
          className="chat-back-btn"
          type="button"
          aria-label="Volver"
          onClick={onLeaveChat}
        >
          <svg
            className="chat-back-icon"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            strokeWidth="2"
            strokeLinecap="round"
            strokeLinejoin="round"
          >
            <polyline points="15 18 9 12 15 6" />
          </svg>
        </button>
        <div
          className="chat-title"
          onClick={(event) => onOpenUserProfile(selectedChat, event)}
        >
          <div className="chat-avatar" aria-hidden="true">
            {selectedChat.avatarUrl ? (
              <img
                src={selectedChat.avatarUrl}
                alt={selectedChat.name}
                className="chat-avatar-image"
                onError={onSelectedAvatarError}
              />
            ) : (
              selectedChat.avatar
            )}
          </div>
          <div className="chat-title-text">
            <span className="chat-name">{selectedChat.name}</span>
            <span className="chat-status">En linea</span>
          </div>
        </div>
      </div>

      <div className="messages" ref={containerRef}>
        {hasMoreHistory && (
          <div ref={topSentinelRef} className="messages-load-older" aria-live="polite">
            {loadingOlder ? "Cargando mensajes anteriores…" : ""}
          </div>
        )}
        {historyStatus === "error" && currentMessages.length === 0 && (
          <div className="message received" role="alert">
            No se pudo cargar la conversación.{" "}
            <button type="button" className="messages-retry-btn" onClick={onRetryHistory}>Reintentar</button>
          </div>
        )}
        {historyStatus === "loading" && currentMessages.length === 0 && (
          <div className="message received" role="status">Cargando mensajes…</div>
        )}
        {historyStatus === "loaded" && currentMessages.length === 0 && (
          <div className="message received">Empieza la conversacion con @{selectedChat.name}</div>
        )}

        {currentMessages.map((message, index) => {
          const isSent = message.senderUsername === currentUsername;
          const showTimeMarker = shouldRenderTimeMarker(currentMessages, index);
          const markerLabel = formatMarkerTime(message.sentAt);

          return (
            <div key={`${message.id}-${index}`} className="message-row">
              {showTimeMarker && markerLabel && (
                <div className="message-time-marker">{markerLabel}</div>
              )}

              <div
                className={`message ${isSent ? "sent" : "received"}`}
              >
                {message.content}
              </div>
            </div>
          );
        })}
        <div ref={messagesEndRef} />
      </div>

      {!websocketConnected && CONNECTION_NOTICES[websocketStatus] && (
        <p className="message-connection-status" role="status">{CONNECTION_NOTICES[websocketStatus]}</p>
      )}
      {sendError && (
        <p className="message-send-error" role="alert">{sendError}</p>
      )}
      <div className="message-input">
        <input
          type="text"
          placeholder="Escribe un mensaje..."
          maxLength={CHAT.MAX_MESSAGE_LENGTH}
          aria-label="Mensaje"
          value={draftMessage}
          onChange={(e) => onDraftChange(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === "Enter") {
              onSendMessage();
            }
          }}
        />
        {draftMessage.length >= COUNTER_THRESHOLD && (
          <span className="message-counter">
            {draftMessage.length}/{CHAT.MAX_MESSAGE_LENGTH}
          </span>
        )}
        <button
          type="button"
          onClick={onSendMessage}
          disabled={!draftMessage.trim() || !websocketConnected}
        >
          Enviar
        </button>
      </div>
    </>
  );
};

export default ChatPane;
