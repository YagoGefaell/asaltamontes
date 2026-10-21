import { CHAT } from "../../../config/constants";

// Show the counter only when it matters
const COUNTER_THRESHOLD = CHAT.MAX_MESSAGE_LENGTH - 200;

const ChatPane = ({
  selectedChat,
  currentMessages,
  currentUsername,
  draftMessage,
  onDraftChange,
  onSendMessage,
  sendError,
  onLeaveChat,
  onOpenUserProfile,
  onSelectedAvatarError,
  websocketConnected,
  messagesEndRef,
}) => {
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

    return date.toLocaleTimeString("es-ES", {
      hour: "2-digit",
      minute: "2-digit",
      hour12: false,
      timeZone: "Europe/Madrid",
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

      <div className="messages">
        {currentMessages.length === 0 && (
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
