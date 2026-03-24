import "./Messages.css";
import { useEffect, useState } from "react";
import { useLocation, useNavigate } from "react-router-dom";
import Loading from "../../shared/components/Loading";
import { getMyConversationsRequest } from "../../features/messages/services/messages.service.js";

const mapConversationToChat = (conversation) => {
  const unread = Number(conversation?.unreadCount || 0);
  const username = conversation?.contactUsername || "Usuario";

  return {
    id: conversation?.conversationId,
    name: username,
    lastMsg: unread > 0 ? `${unread} mensajes sin leer` : "Sin mensajes todavía",
    avatar: username.charAt(0).toUpperCase(),
  };
};

const Messages = () => {
  const navigate = useNavigate();
  const location = useLocation();

  const [chats, setChats] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [selectedChat, setSelectedChat] = useState(null);

  useEffect(() => {
    let mounted = true;

    const fetchConversations = async () => {
      setLoading(true);
      setError("");

      try {
        const conversations = await getMyConversationsRequest();
        if (!mounted) return;

        const mappedChats = conversations.map(mapConversationToChat);
        setChats(mappedChats);

        const selectedConversation = location.state?.selectedConversation;
        if (selectedConversation) {
          const selectedId = selectedConversation.conversationId;
          const existing = mappedChats.find((chat) => chat.id === selectedId);

          if (existing) {
            setSelectedChat(existing);
          } else {
            setSelectedChat(mapConversationToChat(selectedConversation));
          }
        }
      } catch (err) {
        if (mounted) {
          setChats([]);
          setError(err.message || "Error al cargar conversaciones");
        }
      } finally {
        if (mounted) {
          setLoading(false);
        }
      }
    };

    fetchConversations();

    return () => {
      mounted = false;
    };
  }, [location.state]);

  useEffect(() => {
    const className = "chat-open";

    if (selectedChat) {
      document.body.classList.add(className);
    } else {
      document.body.classList.remove(className);
    }

    return () => {
      document.body.classList.remove(className);
    };
  }, [selectedChat]);

  return (
    <div className="messages-page">
      {!selectedChat && (
        <>
          <div className="messages-header">
            <button
              className="back-button"
              type="button"
              aria-label="Volver"
              onClick={() => navigate(-1)}
            >
              ←
            </button>
            <h2 className="messages-title">Mensajes</h2>
          </div>

          <div className="contacts-list">
            {loading && <Loading />}

            {!loading && error && <p className="contact-preview">{error}</p>}

            {!loading && !error && chats.length === 0 && (
              <p className="contact-preview">Aun no tienes conversaciones.</p>
            )}

            {!loading && !error && chats.map((chat) => (
              <button
                key={chat.id}
                type="button"
                className="contact-item"
                onClick={() => setSelectedChat(chat)}
              >
                <div className="contact-avatar" aria-hidden="true">
                  {chat.avatar}
                </div>
                <div className="contact-info">
                  <span className="contact-name">{chat.name}</span>
                  <span className="contact-preview">{chat.lastMsg}</span>
                </div>
              </button>
            ))}
          </div>
        </>
      )}

      {selectedChat && (
        <>
          <div className="messages-header">
            <button
              className="back-button"
              type="button"
              aria-label="Volver"
              onClick={() => setSelectedChat(null)}
            >
              ←
            </button>
            <div className="chat-title">
              <div className="chat-avatar" aria-hidden="true">
                {selectedChat.avatar}
              </div>
              <div className="chat-title-text">
                <span className="chat-name">{selectedChat.name}</span>
                <span className="chat-status">En linea</span>
              </div>
            </div>
          </div>

          <div className="messages">
            <div className="message received">
              Conversacion abierta con @{selectedChat.name}. El historial de mensajes llegara en el siguiente paso.
            </div>
          </div>

          <div className="message-input">
            <input type="text" placeholder="Escribe un mensaje..." />
            <button>Enviar</button>
          </div>
        </>
      )}
    </div>
  );
};

export default Messages;
