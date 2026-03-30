import "./Messages.css";
import { useEffect, useRef, useState, useContext, useCallback } from "react";
import { useLocation, useNavigate } from "react-router-dom";
import Loading from "../../shared/components/Loading";
import { useAuth } from "../../features/auth/hooks/useAuth";

import { ConversationsContext } from "../../features/messages/hooks/conversations.context";

const Messages = () => {
  const navigate = useNavigate();
  const location = useLocation();
  const { currentUser, sendMessage, websocketConnected } = useAuth();

  const { 
    chats, 
    setChats,
    chatMessages,
    setChatMessages,
    setActiveChatId,
    upsertConversation,
    ensurePrivateConversation,
    loadConversationMessages,
    loadingChats, 
    errorChats
  } = useContext(ConversationsContext);

  const [selectedChat, setSelectedChat] = useState(() => {
    const selectedConversation = location.state?.selectedConversation;

    if (!selectedConversation || selectedConversation.conversationId) {
      return null;
    }

    const username = selectedConversation.displayName
      || selectedConversation.contactUsername
      || selectedConversation.username
      || selectedConversation.name
      || "usuario";

    return {
      id: null,
      name: username,
      avatar: username.trim().charAt(0).toUpperCase() || "?",
      avatarUrl: selectedConversation.displayImage || selectedConversation.avatarUrl || null,
      unreadCount: 0,
      lastMsg: selectedConversation.lastMessage || "Empieza la conversacion",
    };
  });
  const [draftMessage, setDraftMessage] = useState("");

  const messagesEndRef = useRef(null);

  const currentMessages = selectedChat ? (chatMessages[selectedChat.id] || []) : [];

  const scrollToBottom = () => {
    if (messagesEndRef.current) {
      messagesEndRef.current.scrollIntoView({ behavior: "auto", block: "end" });
    }
  };

  const handleEnterChat = useCallback(async (chat) => {
    setSelectedChat(chat);
    setActiveChatId(chat.id || null);
    if (chat.id) {
      await loadConversationMessages(chat.id);
    }
  }, [setActiveChatId, loadConversationMessages]);

  const handleLeaveChat = useCallback(() => {
    setSelectedChat(null);
    setActiveChatId(null);
  }, [setActiveChatId]);

  useEffect(() => {
    const selectedConversation = location.state?.selectedConversation;

    if (!selectedConversation || selectedChat) return;

    const conversationId = selectedConversation.conversationId;

    if (!conversationId) {
      navigate(location.pathname, { replace: true, state: {} });
      return;
    }

    const existing = chats.find((c) => String(c.id) === String(conversationId));
    const targetChat = existing || upsertConversation(selectedConversation);

    if (targetChat) {
      setTimeout(() => {
        handleEnterChat(targetChat);
        navigate(location.pathname, { replace: true, state: {} });
      }, 0);
    }
  }, [
    location.state, 
    chats, 
    selectedChat, 
    upsertConversation,
    navigate, 
    location.pathname, 
    handleEnterChat,
  ]);

  useEffect(() => {
    const className = "chat-open";
    if (selectedChat) document.body.classList.add(className);
    else document.body.classList.remove(className);

    return () => document.body.classList.remove(className);
  }, [selectedChat]);

  useEffect(() => {
    if (!selectedChat || selectedChat.id) return;

    const matchedChat = chats.find(
      (chat) => chat.id && String(chat.name).toLowerCase() === String(selectedChat.name).toLowerCase()
    );

    if (!matchedChat) return;

    const syncId = setTimeout(() => {
      const syncedChat = {
        ...matchedChat,
        avatar: matchedChat.avatar || selectedChat.avatar,
        avatarUrl: matchedChat.avatarUrl || selectedChat.avatarUrl || null,
      };

      setSelectedChat(syncedChat);
      setActiveChatId(matchedChat.id);

      setChats((prevChats) => prevChats.map((chat) => (
        String(chat.id) === String(matchedChat.id)
          ? {
              ...chat,
              avatar: chat.avatar || selectedChat.avatar,
              avatarUrl: chat.avatarUrl || selectedChat.avatarUrl || null,
            }
          : chat
      )));

      loadConversationMessages(matchedChat.id);
    }, 0);

    return () => clearTimeout(syncId);
  }, [selectedChat, chats, setActiveChatId, setChats, loadConversationMessages]);

  useEffect(() => {
    if (!selectedChat) return;
    scrollToBottom();
  }, [selectedChat, currentMessages.length]);

  const handleSendMessage = async () => {
    const content = draftMessage.trim();

    if (!content || !selectedChat || !websocketConnected || !currentUser?.username) {
      return;
    }

    let activeChat = selectedChat;
    let conversationId = selectedChat.id;

    if (!conversationId) {
      try {
        const ensuredChat = await ensurePrivateConversation(selectedChat);
        activeChat = ensuredChat;
        conversationId = ensuredChat.id;

        setSelectedChat(ensuredChat);
        setActiveChatId(ensuredChat.id);
      } catch {
        return;
      }
    }

    const payload = {
      conversationId,
      content,
    };

    const wasSent = sendMessage("/app/chat.send", payload);
    if (!wasSent) return;

    setChats((prevChats) => {
      const updated = prevChats.map((chat) => (
        String(chat.id) === String(conversationId)
          ? { ...chat, lastMsg: content }
          : chat
      ));

      const exists = updated.some((chat) => String(chat.id) === String(conversationId));
      if (exists) return updated;

      return [
        {
          ...activeChat,
          id: conversationId,
          lastMsg: content,
          unreadCount: 0,
        },
        ...updated,
      ];
    });

    setDraftMessage("");
  };

  return (
    <div className="messages-page">
      {!selectedChat && (
        <>
          <div className="messages-header">
            <h2 className="messages-title">Mensajes</h2>
          </div>

          <div className="contacts-list">
            {loadingChats && <Loading />}

            {!loadingChats && errorChats && <p className="contact-preview">{errorChats}</p>}

            {!loadingChats && !errorChats && chats.length === 0 && (
              <p className="contact-preview">Aun no tienes conversaciones.</p>
            )}

            {!loadingChats && !errorChats && chats.map((chat) => (
              <button
                key={chat.id}
                type="button"
                className="contact-item"
                onClick={() => handleEnterChat(chat)}
              >
                <div className="avatar-wrapper">
                  <div className="contact-avatar" aria-hidden="true">
                    {chat.avatarUrl ? (
                      <img
                        src={chat.avatarUrl}
                        alt={chat.name}
                        className="contact-avatar-image"
                        onError={() => {
                          setChats((prevChats) => prevChats.map((existingChat) => (
                            String(existingChat.id) === String(chat.id)
                              ? { ...existingChat, avatarUrl: null }
                              : existingChat
                          )));
                        }}
                      />
                    ) : (
                      chat.avatar
                    )}
                  </div>
                </div>

                <div className="contact-info">
                  <span className="contact-name">{chat.name}</span>
                  <span className="contact-preview">
                    {chat.lastMsg}
                  </span>
                </div>

                {chat.unreadCount > 0 && <span className="unread-dot" aria-label="Mensajes sin leer" />}
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
              onClick={handleLeaveChat} // ¡Usamos nuestra nueva función!
            >
              ←
            </button>
            <div className="chat-title">
              <div className="chat-avatar" aria-hidden="true">
                {selectedChat.avatarUrl ? (
                  <img
                    src={selectedChat.avatarUrl}
                    alt={selectedChat.name}
                    className="chat-avatar-image"
                    onError={() => {
                      setSelectedChat((prev) => (prev ? { ...prev, avatarUrl: null } : prev));
                    }}
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