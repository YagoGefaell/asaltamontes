import { useEffect, useLayoutEffect, useRef } from "react";
import { CHAT } from "../../../config/constants";
import { FaInfoCircle } from "react-icons/fa";
import Button from "../../../shared/forms/Button";
import PageHeader from "../../../shared/ui/PageHeader";
import EmptyState from "../../../shared/ui/EmptyState";
import Skeleton from "../../../shared/ui/Skeleton";
import { systemMessageText } from "../../../features/messages/utils/systemMessage";
import { formatDayLabel, formatMessageTime, isSameDay, parseMessageDate } from "../../../features/messages/utils/messageDates";
import { AUTHOR_ROLE_LABELS, canDeleteMessage } from "../../../features/messages/utils/messageModeration";

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
  onOpenGroupInfo,
  moderationRole = null,
  onDeleteMessage,
  onSelectedAvatarError,
  websocketConnected,
  websocketStatus = "connected",
  messagesEndRef,
}) => {
  const isGroup = selectedChat.type === "GROUP";
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

  const shouldRenderTimeMarker = (messages, index) => {
    if (index === 0) return true;

    const currentDate = parseMessageDate(messages[index]?.sentAt);
    const previousDate = parseMessageDate(messages[index - 1]?.sentAt);

    if (!currentDate || !previousDate) return false;

    const oneHourMs = 60 * 60 * 1000;
    return (currentDate.getTime() - previousDate.getTime()) > oneHourMs;
  };

  // A new calendar day starts here (also the very first message)
  const dayLabelFor = (messages, index) => {
    const currentDate = parseMessageDate(messages[index]?.sentAt);
    if (!currentDate) return "";
    const previousDate = index > 0 ? parseMessageDate(messages[index - 1]?.sentAt) : null;
    if (previousDate && isSameDay(currentDate, previousDate)) return "";
    return formatDayLabel(messages[index].sentAt);
  };

  return (
    <>
      <PageHeader
        onBack={onLeaveChat}
        backLabel="Volver"
        actions={isGroup && onOpenGroupInfo && (
          <button type="button" onClick={onOpenGroupInfo} aria-label="Info del grupo" title="Info del grupo">
            <FaInfoCircle aria-hidden="true" />
          </button>
        )}
      >
        <div
          className="chat-title"
          onClick={(event) => (isGroup && onOpenGroupInfo ? onOpenGroupInfo() : onOpenUserProfile(selectedChat, event))}
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
            <span className="chat-status">{isGroup ? "Toca para ver el grupo" : "En linea"}</span>
          </div>
        </div>
      </PageHeader>

      <div
        className="messages"
        ref={containerRef}
        role="log"
        aria-live="polite"
        aria-relevant="additions"
        aria-label={`Mensajes con ${selectedChat.name}`}
      >
        {hasMoreHistory && (
          <div ref={topSentinelRef} className="messages-load-older" aria-live="polite">
            {loadingOlder ? "Cargando mensajes anteriores…" : ""}
          </div>
        )}
        {historyStatus === "error" && currentMessages.length === 0 && (
          <EmptyState
            title="No se pudo cargar la conversación."
            action={<Button variant="secondary" size="small" onClick={onRetryHistory}>Reintentar</Button>}
          />
        )}
        {historyStatus === "loading" && currentMessages.length === 0 && (
          <div role="status" aria-label="Cargando mensajes" className="messages-skeleton">
            <Skeleton width="55%" height="2.25rem" />
            <Skeleton width="40%" height="2.25rem" className="messages-skeleton__sent" />
            <Skeleton width="65%" height="2.25rem" />
          </div>
        )}
        {historyStatus === "loaded" && currentMessages.length === 0 && (
          <EmptyState
            title={`Empieza la conversación con @${selectedChat.name}`}
            description="Escribe el primer mensaje abajo."
          />
        )}

        {currentMessages.map((message, index) => {
          const isSystem = message.type === "SYSTEM";
          const isSent = message.senderUsername === currentUsername;
          const showTimeMarker = shouldRenderTimeMarker(currentMessages, index);
          const markerLabel = formatMessageTime(message.sentAt);
          const dayLabel = dayLabelFor(currentMessages, index);

          return (
            <div key={`${message.id}-${index}`} className="message-row">
              {dayLabel && (
                <div className="message-day-separator" role="separator" aria-label={dayLabel}>
                  <span>{dayLabel}</span>
                </div>
              )}
              {showTimeMarker && markerLabel && (
                <div className="message-time-marker">{markerLabel}</div>
              )}

              {isSystem ? (
                // Group events (T12): centered, no sender, text composed from the payload
                <div className="message-system">{systemMessageText(message, currentUsername)}</div>
              ) : message.deleted ? (
                // Deleted by the moderation (T20d): the message stays as a notice while the chat is open
                <div className="message-system message-deleted">Mensaje eliminado por moderación</div>
              ) : (
                <div className={`message-line ${isSent ? "sent" : "received"}`}>
                  <div className="message-body">
                    {isGroup && !isSent && message.senderUsername && (
                      <div className="message-author">
                        <span className="message-author__name">@{message.senderUsername}</span>
                        {AUTHOR_ROLE_LABELS[message.authorRole] && (
                          <span className={`message-role message-role--${message.authorRole.toLowerCase()}`}>
                            {AUTHOR_ROLE_LABELS[message.authorRole]}
                          </span>
                        )}
                      </div>
                    )}
                    <div className={`message ${isSent ? "sent" : "received"}`}>
                      {message.content}
                    </div>
                  </div>
                  {onDeleteMessage && canDeleteMessage(message, { moderationRole, currentUsername }) && (
                    <button
                      type="button"
                      className="message-delete-btn"
                      aria-label={`Eliminar mensaje de ${message.senderUsername}`}
                      onClick={() => onDeleteMessage(message)}
                    >
                      <svg viewBox="0 0 24 24" width="18" height="18" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
                        <polyline points="3 6 5 6 21 6" />
                        <path d="M19 6l-1 14a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2L5 6" />
                        <path d="M10 11v6M14 11v6M9 6V4a1 1 0 0 1 1-1h4a1 1 0 0 1 1 1v2" />
                      </svg>
                    </button>
                  )}
                </div>
              )}
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
          <span className="message-counter" aria-live="polite">
            {draftMessage.length}/{CHAT.MAX_MESSAGE_LENGTH}
          </span>
        )}
        <Button
          className="message-send-btn"
          onClick={onSendMessage}
          disabled={!draftMessage.trim() || !websocketConnected}
        >
          Enviar
        </Button>
      </div>
    </>
  );
};

export default ChatPane;
