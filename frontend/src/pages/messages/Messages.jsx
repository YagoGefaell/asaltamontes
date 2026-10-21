import "./Messages.css";
import { useEffect, useRef, useState, useContext, useCallback } from "react";
import { useLocation, useNavigate } from "react-router-dom";
import { useAuth } from "../../features/auth/hooks/useAuth";
import { formatters } from "../../utils/helpers";
import ConversationsPane from "./components/ConversationsPanel";
import ChatPane from "./components/ChatPanel";
import GroupCreatorPanel from "./components/GroupCreatorPanel";

import { ConversationsContext } from "../../features/messages/hooks/conversations.context";

const Messages = () => {
  const navigate = useNavigate();
  const location = useLocation();
  const { currentUser, sendMessage, websocketConnected } = useAuth();

  const { 
    chats, 
    setChats,
    chatMessages,
    setActiveChatId,
    upsertConversation,
    ensurePrivateConversation,
    createGroupConversation,
    loadConversationMessages,
    loadingChats, 
    errorChats,
    chatError,
    clearChatError
  } = useContext(ConversationsContext);

  const [selectedChat, setSelectedChat] = useState(() => {
    const selectedConversation = location.state?.selectedConversation;

    if (!selectedConversation || selectedConversation.conversationId) {
      return null;
    }

    const username = selectedConversation.displayName
      || selectedConversation.username
      || selectedConversation.name
      || "usuario";

    return {
      id: null,
      name: username,
      avatar: username.trim().charAt(0).toUpperCase() || "?",
      avatarUrl: formatters.avatar(selectedConversation.displayImage || selectedConversation.avatarUrl, username, username),
      unreadCount: 0,
      lastMsg: selectedConversation.lastMessage || "Empieza la conversacion",
    };
  });
  const [draftMessage, setDraftMessage] = useState("");
  const [sendError, setSendError] = useState(null);
  // Text of the last message sent, to give it back to the input if the server rejects it
  const lastSentRef = useRef(null);
  const [creatingGroup, setCreatingGroup] = useState(false);
  const [isGroupCreatorOpen, setIsGroupCreatorOpen] = useState(false);

  const messagesEndRef = useRef(null);

  const currentMessages = selectedChat ? (chatMessages[selectedChat.id] || []) : [];

  const formatMessageTime = useCallback((dateLike) => {
    if (!dateLike) return "";

    const rawValue = String(dateLike).trim();
    const hasTimezone = /[zZ]|[+-]\d{2}:?\d{2}$/.test(rawValue);
    const normalizedValue = hasTimezone ? rawValue : `${rawValue}Z`;
    const date = new Date(normalizedValue);
    if (Number.isNaN(date.getTime())) return "";

    return date.toLocaleTimeString("es-ES", {
      hour: "2-digit",
      minute: "2-digit",
      hour12: false,
      timeZone: "Europe/Madrid",
    });
  }, []);

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

  const handleOpenUserProfile = useCallback((chat, event = null) => {
    event?.stopPropagation();

    if (!chat || chat.type !== "INDIVIDUAL" || !chat.name) return;

    navigate(`/users/${encodeURIComponent(chat.name)}`);
  }, [navigate]);

  const handleLeaveChat = useCallback(() => {
    setSelectedChat(null);
    setActiveChatId(null);
    navigate("/messages", { replace: true, state: {} });
  }, [navigate, setActiveChatId]);

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

  // The server rejected a message (not a participant, empty, too long…): show why and restore the text
  useEffect(() => {
    if (!chatError) return;
    const failedText = lastSentRef.current;
    lastSentRef.current = null;
    const restoreId = setTimeout(() => {
      setSendError(chatError.detail);
      if (failedText) {
        setDraftMessage((current) => current || failedText);
      }
      clearChatError();
    }, 0);
    return () => clearTimeout(restoreId);
  }, [chatError, clearChatError]);

  const handleDraftChange = (value) => {
    setDraftMessage(value);
    if (sendError) setSendError(null);
  };

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
    if (!wasSent) {
      setSendError("No hay conexión con el chat. Inténtalo de nuevo en unos segundos.");
      return;
    }
    lastSentRef.current = content;
    setSendError(null);

    setChats((prevChats) => {
      const isGroupChat = selectedChat?.type === "GROUP";
      const senderLabel = isGroupChat ? (currentUser?.username || "usuario") : "Tu";
      const myPreview = `${senderLabel}: ${content}`;
      const sentAt = new Date().toISOString();

      const existing = prevChats.find(
        (chat) => String(chat.id) === String(conversationId)
      );

      const updatedChat = {
        ...(existing || activeChat),
        id: conversationId,
        lastMsg: myPreview,
        lastMessageAt: sentAt,
        unreadCount: 0,
      };

      const remainingChats = prevChats.filter(
        (chat) => String(chat.id) !== String(conversationId)
      );

      return [updatedChat, ...remainingChats];
    });

    setDraftMessage("");
  };

  const closeGroupCreator = useCallback(() => {
    if (creatingGroup) return;
    setIsGroupCreatorOpen(false);
  }, [creatingGroup]);

  const openGroupCreator = useCallback(() => {
    setIsGroupCreatorOpen(true);
  }, []);

  const handleCreateGroup = useCallback(async ({ groupName, groupImage, users }) => {
    if (creatingGroup) return;
    setCreatingGroup(true);

    try {
      const createdChat = await createGroupConversation({
        groupName,
        groupImage,
        users,
      });

      if (createdChat?.id) {
        setIsGroupCreatorOpen(false);
        await handleEnterChat(createdChat);
      }
    } finally {
      setCreatingGroup(false);
    }
  }, [
    creatingGroup,
    createGroupConversation,
    handleEnterChat,
  ]);

  return (
    <div className="messages-page">
      {!selectedChat && !isGroupCreatorOpen && (
        <ConversationsPane
          loadingChats={loadingChats}
          errorChats={errorChats}
          chats={chats}
          onEnterChat={handleEnterChat}
          onCreateGroup={openGroupCreator}
          creatingGroup={creatingGroup}
          onAvatarError={(chatId) => {
            setChats((prevChats) => prevChats.map((existingChat) => (
              String(existingChat.id) === String(chatId)
                ? { ...existingChat, avatarUrl: null }
                : existingChat
            )));
          }}
          formatMessageTime={formatMessageTime}
        />
      )}

      {!selectedChat && isGroupCreatorOpen && (
        <GroupCreatorPanel
          isOpen={isGroupCreatorOpen}
          creatingGroup={creatingGroup}
          currentUsername={currentUser?.username}
          onClose={closeGroupCreator}
          onSubmitGroup={handleCreateGroup}
        />
      )}

      {selectedChat && (
        <ChatPane
          selectedChat={selectedChat}
          currentMessages={currentMessages}
          currentUsername={currentUser?.username}
          draftMessage={draftMessage}
          onDraftChange={handleDraftChange}
          sendError={sendError}
          onSendMessage={handleSendMessage}
          onLeaveChat={handleLeaveChat}
          onOpenUserProfile={handleOpenUserProfile}
          onSelectedAvatarError={() => {
            setSelectedChat((prev) => (prev ? { ...prev, avatarUrl: null } : prev));
          }}
          websocketConnected={websocketConnected}
          messagesEndRef={messagesEndRef}
        />
      )}
    </div>
  );
};

export default Messages;