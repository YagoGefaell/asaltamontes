import { FaPlus } from "react-icons/fa";
import Skeleton from "../../../shared/ui/Skeleton";
import PageHeader from "../../../shared/ui/PageHeader";
import EmptyState from "../../../shared/ui/EmptyState";
import Button from "../../../shared/forms/Button";
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
  onBack,
  hasMoreChats = false,
  loadingMoreChats = false,
  onLoadMoreChats,
}) => {
  return (
    <>
      <PageHeader
        title="Mensajes"
        onBack={onBack}
        actions={
          <button type="button" onClick={onCreateGroup} disabled={creatingGroup} aria-label="Crear grupo" title="Crear grupo">
            <FaPlus aria-hidden="true" />
          </button>
        }
      />

      <div className="contacts-list">
        {loadingChats && (
          <div role="status" aria-label="Cargando conversaciones" className="contacts-skeleton">
            {[0, 1, 2, 3].map((i) => (
              <div key={i} className="contacts-skeleton__row">
                <Skeleton circle width="44px" height="44px" />
                <Skeleton width="60%" height="1rem" />
              </div>
            ))}
          </div>
        )}

        {!loadingChats && errorChats && (
          <EmptyState title="No se pudieron cargar las conversaciones." description={errorChats} />
        )}

        {!loadingChats && !errorChats && chats.length === 0 && (
          <EmptyState
            title="Aún no tienes conversaciones."
            description="Escribe a alguien desde su perfil o crea un grupo con el botón +."
          />
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
                <span className="contact-name">
                  {chat.name}
                  {chat.type === "INDIVIDUAL" && chat.username && chat.username !== chat.name && (
                    <span className="contact-username"> @{chat.username}</span>
                  )}
                </span>
                <span className="contact-preview">{chat.lastMsg}</span>
              </div>

              {chat.lastMessageAt && (
                <span className="contact-time">{formatMessageTime(chat.lastMessageAt)}</span>
              )}

              {chat.unreadCount > 0 && (
                <span className="unread-count" role="img" aria-label={`${chat.unreadCount} mensajes sin leer`}>
                  {chat.unreadCount > 99 ? "99+" : chat.unreadCount}
                </span>
              )}
            </button>
          );
        })}

        {!loadingChats && !errorChats && hasMoreChats && (
          <Button
            variant="ghost"
            className="contacts-load-more"
            onClick={onLoadMoreChats}
            disabled={loadingMoreChats}
          >
            {loadingMoreChats ? "Cargando…" : "Cargar más conversaciones"}
          </Button>
        )}
      </div>
    </>
  );
};

export default ConversationsPane;
