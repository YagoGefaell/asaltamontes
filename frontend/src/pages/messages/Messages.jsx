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
    loadConversationMessages,
    loadingChats, 
    errorChats
  } = useContext(ConversationsContext);

  const [selectedChat, setSelectedChat] = useState(null);
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
    setActiveChatId(chat.id);
    await loadConversationMessages(chat.id);
  }, [setActiveChatId, loadConversationMessages]);

  const handleLeaveChat = useCallback(() => {
    setSelectedChat(null);
    setActiveChatId(null);
  }, [setActiveChatId]);

  useEffect(() => {
    const selectedConversation = location.state?.selectedConversation;

    if (!selectedConversation || selectedChat) return;

    const existing = chats.find(
      (c) => String(c.id) === String(selectedConversation.conversationId)
    );

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
    handleEnterChat
  ]);

  useEffect(() => {
    const className = "chat-open";
    if (selectedChat) document.body.classList.add(className);
    else document.body.classList.remove(className);

    return () => document.body.classList.remove(className);
  }, [selectedChat]);

  useEffect(() => {
    if (!selectedChat) return;
    scrollToBottom();
  }, [selectedChat, currentMessages.length]);

  const handleSendMessage = () => {
    const content = draftMessage.trim();

    if (!content || !selectedChat || !websocketConnected || !currentUser?.username) {
      return;
    }

    const payload = {
      conversationId: selectedChat.id,
      senderUsername: currentUser.username,
      receiverUsername: selectedChat.name,
      content,
    };

    const wasSent = sendMessage("/app/chat.private", payload);
    if (!wasSent) return;

    const optimisticMessage = {
      id: `tmp-${Date.now()}`,
      conversationId: selectedChat.id,
      senderUsername: currentUser.username,
      content,
    };

    setChatMessages((prev) => ({
      ...prev,
      [selectedChat.id]: [...(prev[selectedChat.id] || []), optimisticMessage],
    }));

    setChats((prevChats) => prevChats.map((chat) => (
      String(chat.id) === String(selectedChat.id)
        ? { ...chat, lastMsg: content }
        : chat
    )));

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
                      <img src={chat.avatarUrl} alt={chat.name} className="contact-avatar-image" />
                    ) : (
                      chat.avatar
                    )}
                  </div>
                  
                  {chat.unreadCount > 0 && (
                    <span className="unread-badge">{chat.unreadCount > 9 ? "9+" : chat.unreadCount}</span>
                  )}
                </div>

                <div className="contact-info">
                  <span className="contact-name">{chat.name}</span>
                  <span className="contact-preview">
                    {chat.lastMsg}
                  </span>
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
              onClick={handleLeaveChat} // ¡Usamos nuestra nueva función!
            >
              ←
            </button>
            <div className="chat-title">
              <div className="chat-avatar" aria-hidden="true">
                {selectedChat.avatarUrl ? (
                  <img src={selectedChat.avatarUrl} alt={selectedChat.name} className="chat-avatar-image" />
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