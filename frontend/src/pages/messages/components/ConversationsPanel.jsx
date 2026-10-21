import Loading from "../../../shared/components/Loading";
import { formatters } from "../../../utils/helpers";

const ConversationsPane = ({
  loadingChats,
  errorChats,
  chats,
  onEnterChat,
  onAvatarError,
  formatMessageTime,
  onCreateGroup,
  creatingGroup,
  hasMoreChats = false,
  loadingMoreChats = false,
  onLoadMoreChats,
}) => {
  return (
    <>
      <div className="messages-header">
        <h2 className="messages-title">Mensajes</h2>
        <div className="messages-header-actions">
          <button
            type="button"
            className="create-group-btn"
            aria-label="Crear grupo"
            title="Crear grupo"
            onClick={onCreateGroup}
            disabled={creatingGroup}
          >
            <svg
              className="create-group-icon"
              viewBox="0 0 24 24"
              fill="none"
              xmlns="http://www.w3.org/2000/svg"
              aria-hidden="true"
            >
              <path
                d="M12 5V19M5 12H19"
                stroke="currentColor"
                strokeWidth="2"
                strokeLinecap="round"
                strokeLinejoin="round"
              />
            </svg>
          </button>
        </div>
      </div>

      <div className="contacts-list">
        {loadingChats && <Loading />}

        {!loadingChats && errorChats && <p className="contact-preview">{errorChats}</p>}

        {!loadingChats && !errorChats && chats.length === 0 && (
          <p className="no-results">Aún no tienes conversaciones.</p>
        )}

        {!loadingChats && !errorChats && chats.map((chat) => {
          const avatarUrl = formatters.avatar(chat.avatarUrl, chat.name, chat.name);
          const initial = formatters.getInitial(chat.name, chat.name);
          
          return (
            <button
              key={chat.id}
              type="button"
              className="contact-item"
              onClick={() => onEnterChat(chat)}
            >
              <div className="avatar-wrapper">
                <div className="contact-avatar" aria-hidden="true">
                  {chat.avatarUrl ? (
                    <img
                      src={avatarUrl}
                      alt={chat.name}
                      className="contact-avatar-image"
                      onError={() => onAvatarError(chat.id)}
                    />
                  ) : (
                    initial
                  )}
                </div>
              </div>

              <div className="contact-info">
                <span className="contact-name">{chat.name}</span>
                <span className="contact-preview">{chat.lastMsg}</span>
              </div>

              {chat.lastMessageAt && (
                <span className="contact-time">{formatMessageTime(chat.lastMessageAt)}</span>
              )}

              {chat.unreadCount > 0 && <span className="unread-dot" aria-label="Mensajes sin leer" />}
            </button>
          );
        })}

        {!loadingChats && !errorChats && hasMoreChats && (
          <button
            type="button"
            className="contacts-load-more"
            onClick={onLoadMoreChats}
            disabled={loadingMoreChats}
          >
            {loadingMoreChats ? "Cargando…" : "Cargar más conversaciones"}
          </button>
        )}
      </div>
    </>
  );
};

export default ConversationsPane;
