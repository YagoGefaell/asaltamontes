import { useEffect, useLayoutEffect, useRef } from "react";
import { CHAT } from "../../../config/constants";
import { FaPaperPlane } from "react-icons/fa";
import Avatar from "../../../shared/ui/Avatar";
import Button from "../../../shared/forms/Button";
import PageHeader from "../../../shared/ui/PageHeader";
import EmptyState from "../../../shared/ui/EmptyState";
import Skeleton from "../../../shared/ui/Skeleton";
import ReportMenu from "../../../features/reports/components/ReportMenu";
import { systemMessageText } from "../../../features/messages/utils/systemMessage";
import { formatDayLabel, formatMessageTime, isSameDay, parseMessageDate } from "../../../features/messages/utils/messageDates";
import { groupSubtitle } from "../../../features/messages/utils/groupSubtitle";
import { canDeleteMessage } from "../../../features/messages/utils/messageModeration";

// Show the counter only when it matters
const COUNTER_THRESHOLD = CHAT.MAX_MESSAGE_LENGTH - 200;

// Consecutive messages of the same author closer than this form one block (T29q)
const BLOCK_GAP_MS = 5 * 60 * 1000;
const MAX_INPUT_HEIGHT_PX = 120; // ~5 lines

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
  olderError = false,
  onLoadOlder,
  historyStatus = "loaded",
  onRetryHistory,
  onLeaveChat,
  onOpenUserProfile,
  onOpenGroupInfo,
  onOpenAuthorProfile,
  groupParticipants = null,
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
  const inputRef = useRef(null);
  const oldestMessageId = currentMessages.length > 0 ? currentMessages[0].id : null;

  // Keep the latest callback in a ref so the observer is not recreated on every render
  const onLoadOlderRef = useRef(onLoadOlder);
  useEffect(() => {
    onLoadOlderRef.current = onLoadOlder;
  });
  const canLoadOlder = Boolean(onLoadOlder);

  // Load older messages when the top of the list becomes visible (infinite scroll upwards)
  useEffect(() => {
    const sentinel = topSentinelRef.current;
    if (!sentinel || !hasMoreHistory || !canLoadOlder || olderError || typeof IntersectionObserver === "undefined") return;

    const observer = new IntersectionObserver((entries) => {
      if (entries.some((entry) => entry.isIntersecting) && !loadingOlder) {
        heightBeforeLoadRef.current = containerRef.current?.scrollHeight ?? null;
        onLoadOlderRef.current?.();
      }
    }, { root: containerRef.current, rootMargin: "200px 0px 0px 0px" });

    observer.observe(sentinel);
    return () => observer.disconnect();
  }, [hasMoreHistory, loadingOlder, canLoadOlder, olderError, oldestMessageId]);

  // After prepending a page, shift the scroll by the added height so nothing jumps
  useLayoutEffect(() => {
    const container = containerRef.current;
    if (!container || heightBeforeLoadRef.current == null) return;
    container.scrollTop += container.scrollHeight - heightBeforeLoadRef.current;
    heightBeforeLoadRef.current = null;
  }, [oldestMessageId]);

  // The textarea grows with the text up to ~5 lines
  useLayoutEffect(() => {
    const input = inputRef.current;
    if (!input) return;
    input.style.height = "auto";
    input.style.height = `${Math.min(input.scrollHeight, MAX_INPUT_HEIGHT_PX)}px`;
  }, [draftMessage]);

  const handleInputKeyDown = (event) => {
    if (event.key !== "Enter" || event.shiftKey || event.nativeEvent?.isComposing) return;
    // Enter sends only with a mouse and keyboard; on touch screens it adds a new line
    const isDesktop = typeof window.matchMedia === "function"
      ? window.matchMedia("(hover: hover) and (pointer: fine)").matches
      : true;
    if (!isDesktop) return;
    event.preventDefault();
    onSendMessage();
  };

  // First message of a block: another author, a gap of 5 minutes or more, a new day or a system/deleted message before
  const startsBlock = (messages, index) => {
    if (index === 0) return true;
    const message = messages[index];
    const previous = messages[index - 1];
    if (previous.type === "SYSTEM" || previous.deleted) return true;
    if (previous.senderUsername !== message.senderUsername) return true;
    if (dayLabelFor(messages, index)) return true;
    const current = parseMessageDate(message.sentAt);
    const before = parseMessageDate(previous.sentAt);
    if (!current || !before) return true;
    return current.getTime() - before.getTime() >= BLOCK_GAP_MS;
  };

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
            <span className="chat-status">
              {isGroup
                ? groupSubtitle(groupParticipants?.names, groupParticipants?.count)
                : `@${selectedChat.name}`}
            </span>
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
            {olderError && !loadingOlder && (
              <>
                <span role="alert">No se pudieron cargar los mensajes anteriores.</span>{" "}
                <Button variant="secondary" size="small" onClick={() => onLoadOlder?.()}>Reintentar</Button>
              </>
            )}
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

          const showAuthor = isGroup && !isSent && !isSystem && !message.deleted && Boolean(message.senderUsername);
          const blockStart = showAuthor && startsBlock(currentMessages, index);
          const authorName = message.senderFullName || message.senderUsername;

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
                <div
                  className={`message-line ${isSent ? "sent" : "received"}${showAuthor ? " message-line--grouped" : ""}`}
                  {...(blockStart && showAuthor ? { role: "group", "aria-label": authorName } : {})}
                >
                  {showAuthor && (
                    blockStart ? (
                      <button
                        type="button"
                        className="message-avatar"
                        aria-label={`Ver perfil de ${authorName}`}
                        onClick={() => onOpenAuthorProfile?.(message.senderUsername)}
                      >
                        <Avatar src={message.senderAvatarUrl} name={authorName} size={28} />
                      </button>
                    ) : (
                      <span className="message-avatar message-avatar--spacer" aria-hidden="true" />
                    )
                  )}
                  <div className="message-body">
                    <div className={`message ${isSent ? "sent" : "received"}`}>
                      {message.content}
                    </div>
                  </div>
                  {!isSent && Boolean(message.senderUsername) && (
                    <ReportMenu
                      className="message-delete-btn"
                      menuTitle="Mensaje"
                      reportTitle="Denunciar mensaje"
                      actionLabel="Denunciar mensaje"
                      targetType="MESSAGE"
                      target={message.id}
                    />
                  )}
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
        <textarea
          ref={inputRef}
          rows={1}
          placeholder="Escribe un mensaje..."
          maxLength={CHAT.MAX_MESSAGE_LENGTH}
          aria-label="Mensaje"
          value={draftMessage}
          onChange={(e) => onDraftChange(e.target.value)}
          onKeyDown={handleInputKeyDown}
        />
        {draftMessage.length >= COUNTER_THRESHOLD && (
          <span className="message-counter" aria-live="polite">
            {draftMessage.length}/{CHAT.MAX_MESSAGE_LENGTH}
          </span>
        )}
        <button
          type="button"
          className="message-send-btn"
          onClick={onSendMessage}
          disabled={!draftMessage.trim() || !websocketConnected}
          aria-label="Enviar mensaje"
        >
          <FaPaperPlane aria-hidden="true" />
        </button>
      </div>
    </>
  );
};

export default ChatPane;
