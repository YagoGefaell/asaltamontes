import { useState, useCallback, useEffect } from "react";
import { useAuth } from "../../auth/hooks/useAuth"; 
import { useGlobalMessageListener } from "../hooks/useGlobalMessageListener";
import { ConversationsContext } from "../hooks/conversations.context";
import {
  getMyConversationsRequest,
  getConversationMessagesRequest,
  markConversationReadRequest,
  getPrivateConversationRequest,
  createPrivateConversationRequest,
  createGroupConversationRequest,
} from "../services/messages.service.js";
import { mergeMessages } from "../utils/mergeMessages.js";

export const ConversationsProvider = ({ children }) => {
  const { websocketClient, websocketConnected, isAuthenticated, sendMessage, currentUser } = useAuth();

  const [chats, setChats] = useState([]);
  // Last error of a message we sent (from /user/queue/errors); Messages shows it and restores the text
  const [chatError, setChatError] = useState(null);
  const [chatMessages, setChatMessages] = useState({});
  // Per conversation: { hasMore, nextBefore, loading } of the history pagination (T10)
  const [historyPaging, setHistoryPaging] = useState({});
  const [loadingChats, setLoadingChats] = useState(false);
  const [errorChats, setErrorChats] = useState(null);

  const [activeChatId, setActiveChatId] = useState(null);

  const clearUnreadCount = useCallback((conversationId) => {
    setChats((prevChats) => prevChats.map((chat) => (
      String(chat.id) === String(conversationId)
        ? { ...chat, unreadCount: 0 }
        : chat
    )));
  }, []);

  const getLastMessageId = useCallback((messages) => {
    if (!Array.isArray(messages) || messages.length === 0) return null;

    const rawLastId = messages[messages.length - 1]?.id;
    const numericId = Number(rawLastId);

    return Number.isInteger(numericId) ? numericId : null;
  }, []);

  const sendReadReceipt = useCallback((conversationId, lastMessageId = null) => {
    if (!conversationId) return;

    // Reading the history no longer marks the chat as read on the server: without WebSocket, use REST
    if (!websocketConnected) {
      markConversationReadRequest(conversationId);
      return;
    }

    sendMessage("/app/chat.read", {
      conversationId,
      lastMessageId,
    });
  }, [sendMessage, websocketConnected]);

  const markConversationAsRead = useCallback((conversationId, messages = null) => {
    if (!conversationId) return;

    clearUnreadCount(conversationId);
    sendReadReceipt(conversationId, getLastMessageId(messages));
  }, [clearUnreadCount, sendReadReceipt, getLastMessageId]);

  const buildPreviewPrefix = useCallback((chatType, senderId, senderName, senderUsername = null) => {
    const rawCurrentUserId = currentUser?.userId;
    const currentUserId = Number(rawCurrentUserId);
    const normalizedSenderId = Number(senderId);

    const isSenderIdValid = Number.isInteger(normalizedSenderId);
    const isCurrentUserIdValid = Number.isInteger(currentUserId);
    const senderIsCurrentUserById = isSenderIdValid && isCurrentUserIdValid && normalizedSenderId === currentUserId;
    const senderIsCurrentUserByUsername = senderUsername && senderUsername === currentUser?.username;
    const senderIsCurrentUser = senderIsCurrentUserById || senderIsCurrentUserByUsername;

    if (chatType === "INDIVIDUAL") {
      return senderIsCurrentUser ? "Tu" : null;
    }

    if (senderName && senderName.trim()) {
      return senderName.trim();
    }

    if (senderUsername && senderUsername.trim()) {
      return senderUsername.trim();
    }

    return null;
  }, [currentUser?.userId, currentUser?.username]);

  const buildLastMessagePreview = useCallback((conversation, senderUsername = null, chatTypeOverride = null) => {
    const lastMessageText = (conversation?.lastMessage || conversation?.content || "").trim();
    if (!lastMessageText) return "Abre la conversacion";

    const chatType = chatTypeOverride || conversation?.type || "INDIVIDUAL";

    const prefix = buildPreviewPrefix(
      chatType,
      conversation?.lastMessageSenderId,
      conversation?.lastMessageSenderName,
      senderUsername
    );

    return prefix ? `${prefix}: ${lastMessageText}` : lastMessageText;
  }, [buildPreviewPrefix]);

  const mapConversationToChat = useCallback((conversation) => {
    const username = conversation?.displayName || conversation?.username || "usuario";
    const firstChar = username.trim().charAt(0).toUpperCase();

    return {
      id: conversation?.conversationId || conversation?.id,
      type: conversation?.type || "INDIVIDUAL",
      name: username,
      avatar: firstChar || "?",
      avatarUrl: conversation?.displayImage || conversation?.avatarUrl || null,
      unreadCount: Number(conversation?.unreadCount || 0),
      lastMsg: buildLastMessagePreview(conversation),
      lastMessageAt: conversation?.lastMessageAt || null,
    };
  }, [buildLastMessagePreview]);

  const upsertConversation = useCallback((conversation) => {
    if (!conversation?.conversationId) return null;

    const mapped = mapConversationToChat(conversation);

    setChats((prevChats) => {
      const exists = prevChats.some(
        (chat) => String(chat.id) === String(mapped.id)
      );
      if (exists) return prevChats;
      return [mapped, ...prevChats];
    });

    return mapped;
  }, [mapConversationToChat]);

  const ensurePrivateConversation = useCallback(async (chat) => {
    if (chat?.id) return chat;

    const recipientUsername = chat?.name;
    if (!recipientUsername) {
      throw new Error("Usuario destino no valido");
    }

    let existingConversation = null;

    try {
      existingConversation = await getPrivateConversationRequest(recipientUsername);
    } catch {
      // Si falla la búsqueda, intentamos crear para no bloquear el primer mensaje.
      existingConversation = null;
    }

    if (existingConversation?.conversationId) {
      const mappedExistingChat = {
        ...chat,
        id: existingConversation.conversationId,
        type: chat.type || "INDIVIDUAL",
        unreadCount: chat.unreadCount || 0,
        lastMsg: chat.lastMsg || "Empieza la conversacion",
        lastMessageAt: chat.lastMessageAt || existingConversation.lastMessageAt || null,
      };

      setChats((prevChats) => {
        const exists = prevChats.some(
          (existingChat) => String(existingChat.id) === String(mappedExistingChat.id)
        );

        if (exists) {
          return prevChats.map((existingChat) => (
            String(existingChat.id) === String(mappedExistingChat.id)
              ? { ...existingChat, ...mappedExistingChat }
              : existingChat
          ));
        }

        return [mappedExistingChat, ...prevChats];
      });

      return mappedExistingChat;
    }

    const createdConversation = await createPrivateConversationRequest(recipientUsername);
    const createdConversationId = createdConversation?.conversationId;

    if (!createdConversationId) {
      throw new Error("No se pudo crear la conversacion");
    }

    const mappedChat = {
      id: createdConversationId,
      type: createdConversation?.type || "INDIVIDUAL",
      name: createdConversation?.displayName || createdConversation?.username || recipientUsername,
      avatar: chat?.avatar || recipientUsername.trim().charAt(0).toUpperCase() || "?",
      avatarUrl: createdConversation?.displayImage || chat?.avatarUrl || null,
      unreadCount: 0,
      lastMsg: buildLastMessagePreview(createdConversation, null, "INDIVIDUAL"),
      lastMessageAt: createdConversation?.lastMessageAt || null,
    };

    setChats((prevChats) => {
      const exists = prevChats.some(
        (existingChat) => String(existingChat.id) === String(createdConversationId)
      );

      if (exists) {
        return prevChats.map((existingChat) => (
          String(existingChat.id) === String(createdConversationId)
            ? { ...existingChat, ...mappedChat }
            : existingChat
        ));
      }

      return [mappedChat, ...prevChats];
    });

    return mappedChat;
  }, [buildLastMessagePreview]);

  const createGroupConversation = useCallback(async ({ groupName, groupImage = null, users = [] }) => {
    const normalizedUsers = Array.isArray(users)
      ? users.map((username) => String(username || "").trim()).filter(Boolean)
      : [];

    if (normalizedUsers.length === 0) {
      throw new Error("Debes indicar al menos un usuario para crear el grupo");
    }

    const createdConversation = await createGroupConversationRequest({
      groupName,
      groupImage,
      users: normalizedUsers,
    });

    const createdConversationId = createdConversation?.conversationId;
    if (!createdConversationId) {
      throw new Error("No se pudo crear el grupo");
    }

    const conversations = await getMyConversationsRequest();
    const mappedChats = Array.isArray(conversations)
      ? conversations.map((conversation) => mapConversationToChat(conversation))
      : [];

    setChats(mappedChats);

    const createdChat = mappedChats.find(
      (chat) => String(chat.id) === String(createdConversationId)
    );

    if (createdChat) {
      return createdChat;
    }

    const safeGroupName = (groupName || "Nuevo grupo").trim() || "Nuevo grupo";

    return {
      id: createdConversationId,
      type: "GROUP",
      name: safeGroupName,
      avatar: safeGroupName.charAt(0).toUpperCase() || "?",
      avatarUrl: groupImage,
      unreadCount: 0,
      lastMsg: "Abre la conversacion",
      lastMessageAt: null,
    };
  }, [mapConversationToChat]);

  const loadConversationMessages = useCallback(async (conversationId) => {
    if (!conversationId) return;

    const alreadyLoaded = chatMessages[conversationId];
    if (alreadyLoaded) {
      markConversationAsRead(conversationId, alreadyLoaded);
      return;
    }

    try {
      const page = await getConversationMessagesRequest(conversationId);
      const merged = mergeMessages(chatMessages[conversationId], page.items);

      // Messages that arrived by WebSocket while the page was loading are kept (and deduplicated)
      setChatMessages((prev) => ({
        ...prev,
        [conversationId]: mergeMessages(prev[conversationId], page.items),
      }));
      setHistoryPaging((prev) => ({
        ...prev,
        [conversationId]: { hasMore: page.hasMore, nextBefore: page.nextBefore, loading: false },
      }));
      markConversationAsRead(conversationId, merged);
    } catch {
      // No bloqueamos la UI del chat si falla la carga inicial del historial.
      markConversationAsRead(conversationId);
    }
  }, [chatMessages, markConversationAsRead]);

  const loadOlderMessages = useCallback(async (conversationId) => {
    const paging = historyPaging[conversationId];
    if (!conversationId || !paging?.hasMore || paging.loading) return;

    setHistoryPaging((prev) => ({ ...prev, [conversationId]: { ...paging, loading: true } }));
    try {
      const page = await getConversationMessagesRequest(conversationId, paging.nextBefore);
      setChatMessages((prev) => ({
        ...prev,
        [conversationId]: mergeMessages(prev[conversationId], page.items),
      }));
      setHistoryPaging((prev) => ({
        ...prev,
        [conversationId]: { hasMore: page.hasMore, nextBefore: page.nextBefore, loading: false },
      }));
    } catch {
      setHistoryPaging((prev) => ({ ...prev, [conversationId]: { ...paging, loading: false } }));
    }
  }, [historyPaging]);

  useEffect(() => {
    if (!isAuthenticated) {
      setChats([]);
      setChatMessages({});
      setHistoryPaging({});
      setLoadingChats(false);
      setErrorChats(null);
      return;
    }

    let mounted = true;

    const loadConversations = async () => {
      setLoadingChats(true);
      setErrorChats(null);

      try {
        const conversations = await getMyConversationsRequest();
        if (!mounted) return;

        const mappedChats = Array.isArray(conversations)
          ? conversations.map((conversation) => mapConversationToChat(conversation))
          : [];

        setChats(mappedChats);
      } catch (error) {
        if (!mounted) return;
        setErrorChats(error.message || "Error cargando conversaciones");
      } finally {
        if (mounted) {
          setLoadingChats(false);
        }
      }
    };

    loadConversations();

    return () => {
      mounted = false;
    };
  }, [isAuthenticated, mapConversationToChat]);

  const handleNewMessage = useCallback((payload) => {
    const conversationId = payload?.conversationId;
    if (!conversationId) return;

    const isCurrentlyReading = String(activeChatId) === String(conversationId);
    if (isCurrentlyReading) {
      const payloadMessageId = Number.isInteger(Number(payload?.id)) ? Number(payload.id) : null;
      sendReadReceipt(conversationId, payloadMessageId);
    }

    setChatMessages((prev) => ({
      ...prev,
      [conversationId]: mergeMessages(prev[conversationId], [{
        id: payload.id,
        content: payload.content,
        senderUsername: payload.senderUsername,
        sentAt: payload.sentAt,
      }]),
    }));

    setChats((prevChats) => {
      const existing = prevChats.find(
        (chat) => String(chat.id) === String(conversationId)
      );

      if (!existing) {
        const fallbackUsername = payload?.senderUsername || payload?.receiverUsername || "usuario";
        const firstChar = fallbackUsername.trim().charAt(0).toUpperCase();
        const fallbackType = "INDIVIDUAL";
        const messageTimestamp = payload?.sentAt || new Date().toISOString();

        return [
          {
            id: conversationId,
            type: fallbackType,
            name: fallbackUsername,
            avatar: firstChar || "?",
            avatarUrl: null,
            unreadCount: isCurrentlyReading ? 0 : 1,
            lastMsg: buildLastMessagePreview(payload, payload.senderUsername, fallbackType),
            lastMessageAt: messageTimestamp,
          },
          ...prevChats,
        ];
      }

      const newUnreadCount = isCurrentlyReading ? 0 : (existing.unreadCount || 0) + 1;
      const updatedChat = {
        ...existing,
        lastMsg: buildLastMessagePreview(payload, payload.senderUsername, existing.type || "INDIVIDUAL"),
        lastMessageAt: payload?.sentAt || existing.lastMessageAt || null,
        unreadCount: newUnreadCount,
      };

      const remainingChats = prevChats.filter(
        (chat) => String(chat.id) !== String(conversationId)
      );

      return [updatedChat, ...remainingChats];
    });
  }, [activeChatId, sendReadReceipt, buildLastMessagePreview]);

  const handleChatError = useCallback((error) => {
    setChatError({ detail: error?.detail || "No se ha podido enviar el mensaje", at: Date.now() });
  }, []);

  const clearChatError = useCallback(() => setChatError(null), []);

  useGlobalMessageListener(
    websocketClient,
    websocketConnected,
    handleNewMessage,
    handleChatError
  );

  const totalUnreadCount = chats.reduce((total, chat) => total + (chat.unreadCount > 0 ? 1 : 0), 0);

  return (
    <ConversationsContext.Provider 
      value={{ 
        chats, 
        setChats, 
        chatMessages, 
        setChatMessages,
        loadingChats,
        errorChats,
        upsertConversation,
        ensurePrivateConversation,
        createGroupConversation,
        loadConversationMessages,
        loadOlderMessages,
        historyPaging,
        activeChatId,
        setActiveChatId,
        totalUnreadCount,
        chatError,
        clearChatError
      }}
    >
      {children}
    </ConversationsContext.Provider>
  );
};