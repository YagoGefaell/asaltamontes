import "./Messages.css";
import { useEffect, useRef, useState } from "react";
import { useLocation, useNavigate } from "react-router-dom";
import Loading from "../../shared/components/Loading";
import {
  getConversationMessagesRequest,
  getMyConversationsRequest,
} from "../../features/messages/services/messages.service.js";
import { useAuth } from "../../features/auth/hooks/useAuth";

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
  const { currentUser, sendMessage, websocketClient, websocketConnected } = useAuth();

  const [chats, setChats] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [selectedChat, setSelectedChat] = useState(null);
  const [chatMessages, setChatMessages] = useState({});
  const [draftMessage, setDraftMessage] = useState("");
  const [loadingHistory, setLoadingHistory] = useState(false);
  const subscriptionRef = useRef(null);
  const messagesEndRef = useRef(null);

  const currentMessages = selectedChat ? (chatMessages[selectedChat.id] || []) : [];

  const scrollToBottom = () => {
    if (messagesEndRef.current) {
      messagesEndRef.current.scrollIntoView({ behavior: "auto", block: "end" });
    }
  };

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

  useEffect(() => {
    if (!selectedChat) return;
    scrollToBottom();
  }, [selectedChat, currentMessages.length, loadingHistory]);

  useEffect(() => {
    let mounted = true;

    const loadConversationHistory = async () => {
      if (!selectedChat?.id) return;

      setLoadingHistory(true);
      try {
        const history = await getConversationMessagesRequest(selectedChat.id);
        if (!mounted) return;

        const normalized = (history || []).map((message) => ({
          id: message.id,
          content: message.content,
          senderUsername: message.senderUsername,
        }));

        setChatMessages((prev) => ({
          ...prev,
          [selectedChat.id]: normalized,
        }));
      } catch (historyError) {
        if (mounted) {
          console.error("Error cargando historial", historyError);
        }
      } finally {
        if (mounted) {
          setLoadingHistory(false);
        }
      }
    };

    loadConversationHistory();

    return () => {
      mounted = false;
    };
  }, [selectedChat]);

  useEffect(() => {
    if (!websocketClient || !websocketConnected || !selectedChat) {
      return;
    }

    if (subscriptionRef.current) {
      subscriptionRef.current.unsubscribe();
      subscriptionRef.current = null;
    }

    subscriptionRef.current = websocketClient.subscribe("/user/queue/messages", (frame) => {
      try {
        const payload = JSON.parse(frame.body);
        const conversationId = payload?.conversationId;

        // Show incoming messages only in the currently open chat.
        if (!conversationId || conversationId !== selectedChat.id) {
          return;
        }

        setChatMessages((prev) => {
          const previous = prev[conversationId] || [];
          return {
            ...prev,
            [conversationId]: [
              ...previous,
              {
                id: payload.id || `${Date.now()}-${Math.random()}`,
                content: payload.content,
                senderUsername: payload.senderUsername,
              },
            ],
          };
        });
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
  }, [websocketClient, websocketConnected, selectedChat]);

  const handleSendMessage = () => {
    const content = draftMessage.trim();

    if (!content || !selectedChat || !websocketClient || !websocketConnected || !currentUser?.username) {
      return;
    }

    const payload = {
      conversationId: selectedChat.id,
      senderUsername: currentUser.username,
      receiverUsername: selectedChat.name,
      content,
    };

    const wasSent = sendMessage("/app/chat.private", payload);
    if (!wasSent) {
      return;
    }

    setChatMessages((prev) => {
      const previous = prev[selectedChat.id] || [];
      return {
        ...prev,
        [selectedChat.id]: [
          ...previous,
          {
            id: `local-${Date.now()}`,
            content,
            senderUsername: currentUser.username,
          },
        ],
      };
    });

    setDraftMessage("");
  };

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
            {loadingHistory && <Loading />}

            {!loadingHistory && currentMessages.length === 0 && (
              <div className="message received">Empieza la conversacion con @{selectedChat.name}</div>
            )}

            {currentMessages.map((message) => {
              const isSent = message.senderUsername === currentUser?.username;
              return (
                <div
                  key={message.id}
                  className={`message ${isSent ? "sent" : "received"}`}
                >
                  {message.content}
                </div>
              );
            })}
            <div ref={messagesEndRef} />
          </div>

          <div className="message-input">
            <input
              type="text"
              placeholder="Escribe un mensaje..."
              value={draftMessage}
              onChange={(e) => setDraftMessage(e.target.value)}
              onKeyDown={(e) => {
                if (e.key === "Enter") {
                  handleSendMessage();
                }
              }}
            />
            <button
              type="button"
              onClick={handleSendMessage}
              disabled={!draftMessage.trim() || !websocketConnected}
            >
              Enviar
            </button>
          </div>
        </>
      )}
    </div>
  );
};

export default Messages;
