import { useState, useCallback, useEffect, useRef } from "react";
import { useAuth } from "../../auth/hooks/useAuth"; 
import { useGlobalMessageListener } from "../hooks/useGlobalMessageListener";
import { ConversationsContext } from "../hooks/conversations.context";
import {
  getMyConversationsRequest,
  getConversationMessagesRequest,
  getMessagesAfterRequest,
  markConversationReadRequest,
  getPrivateConversationRequest,
  createPrivateConversationRequest,
  createGroupConversationRequest,
} from "../services/messages.service.js";
import { mergeMessages } from "../utils/mergeMessages.js";
import { countUnreadChats, mergeChatPages, unreadChatsElsewhere } from "../utils/mergeChats.js";

const INITIAL_CHATS_PAGING = { hasMore: false, nextBeforeAt: null, nextBeforeId: null, loading: false, pages: 0 };

export const ConversationsProvider = ({ children }) => {
  const { websocketClient, websocketConnected, isAuthenticated, sendMessage, currentUser } = useAuth();

  const [chats, setChats] = useState([]);
  // Last error of a message we sent (from /user/queue/errors); Messages shows it and restores the text
  const [chatError, setChatError] = useState(null);
  const [chatMessages, setChatMessages] = useState({});
  // Per conversation: { hasMore, nextBefore, loading } of the history pagination (T10)
  const [historyPaging, setHistoryPaging] = useState({});
  // Conversations whose first page of history failed to load (BUG-061): { [id]: true }
  const [historyError, setHistoryError] = useState({});
  const [loadingChats, setLoadingChats] = useState(false);
  const [errorChats, setErrorChats] = useState(null);

  const [activeChatId, setActiveChatId] = useState(null);

  // Chat list by pages (T10b): cursor of the next page, and unread chats that are not loaded yet
  const [chatsPaging, setChatsPaging] = useState(INITIAL_CHATS_PAGING);
  const [unreadElsewhere, setUnreadElsewhere] = useState(0);
  const chatsRef = useRef(chats);
  useEffect(() => {
    chatsRef.current = chats;
  }, [chats]);

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

  /**
   * Puts a page of the chat list in place. mode: "replace" (first load), "fresh" (the first page again, on top
   * of what is loaded) or "older" (the next page). Returns the resulting list (best estimate, for callers).
   */
  const applyChatPage = useCallback((page, mode) => {
    const mapped = page.items.map((conversation) => mapConversationToChat(conversation));
    const merge = (current) => (mode === "replace"
      ? mapped
      : mergeChatPages(current, mapped, { fresh: mode === "fresh" }));

    const estimated = merge(chatsRef.current);
    setChats(merge);
    setUnreadElsewhere(unreadChatsElsewhere(page.unreadChats, estimated));
    setChatsPaging((prev) => {
      // A fresh first page does not move the cursor once older pages are loaded
      if (mode === "fresh" && prev.pages > 1) return { ...prev, loading: false };
      return {
        hasMore: page.hasMore,
        nextBeforeAt: page.nextBeforeAt,
        nextBeforeId: page.nextBeforeId,
        loading: false,
        pages: mode === "older" ? prev.pages + 1 : 1,
      };
    });
    return estimated;
  }, [mapConversationToChat]);

  const refreshChats = useCallback(async () => {
    const page = await getMyConversationsRequest();
    return applyChatPage(page, "fresh");
  }, [applyChatPage]);

  const loadMoreChats = useCallback(async () => {
    if (!chatsPaging.hasMore || chatsPaging.loading) return;

    setChatsPaging((prev) => ({ ...prev, loading: true }));
    try {
      const page = await getMyConversationsRequest({
        beforeAt: chatsPaging.nextBeforeAt,
        beforeId: chatsPaging.nextBeforeId,
      });
      applyChatPage(page, "older");
    } catch {
      setChatsPaging((prev) => ({ ...prev, loading: false }));
    }
  }, [chatsPaging, applyChatPage]);

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

    // The new group has no messages yet: it may not be in the first page, so it is looked up there or built below
    const mappedChats = await refreshChats();

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
  }, [refreshChats]);

  const loadConversationMessages = useCallback(async (conversationId) => {
    if (!conversationId) return;

    // A live message can create chatMessages[id] before the history is loaded (BUG-051):
    // "loaded" means the first page arrived, which is what historyPaging records
    const alreadyLoaded = historyPaging[conversationId] ? chatMessages[conversationId] : null;
    if (alreadyLoaded) {
      markConversationAsRead(conversationId, alreadyLoaded);
      return;
    }

    setHistoryError((prev) => (prev[conversationId] ? { ...prev, [conversationId]: false } : prev));
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
      // The user has not seen any message: do not mark as read, let the chat offer a retry (BUG-061)
      setHistoryError((prev) => ({ ...prev, [conversationId]: true }));
    }
  }, [chatMessages, historyPaging, markConversationAsRead]);

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
      setChatsPaging(INITIAL_CHATS_PAGING);
      setUnreadElsewhere(0);
      setChatMessages({});
      setHistoryPaging({});
      setHistoryError({});
      setLoadingChats(false);
      setErrorChats(null);
      return;
    }

    let mounted = true;

    const loadConversations = async () => {
      setLoadingChats(true);
      setErrorChats(null);

      try {
        const page = await getMyConversationsRequest();
        if (!mounted) return;
        applyChatPage(page, "replace");
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
  }, [isAuthenticated, applyChatPage]);

  // Latest values for the reconnection catch-up, which must not restart when they change
  const catchUpStateRef = useRef({});
  useEffect(() => {
    catchUpStateRef.current = { chatMessages, activeChatId, markConversationAsRead, applyChatPage };
  });
  const wasConnectedRef = useRef(false);

  // T11: after a reconnection, fetch what arrived while the WebSocket was down
  useEffect(() => {
    if (!isAuthenticated) {
      wasConnectedRef.current = false;
      return;
    }
    if (!websocketConnected) return;
    if (!wasConnectedRef.current) {
      // First connection: the normal loads already have everything
      wasConnectedRef.current = true;
      return;
    }

    let cancelled = false;

    const catchUp = async () => {
      const { chatMessages: cached, applyChatPage: applyPage } = catchUpStateRef.current;

      try {
        const page = await getMyConversationsRequest();
        if (cancelled) return;
        applyPage(page, "fresh");
      } catch {
        // The list stays as it was; new messages keep arriving by WebSocket
      }

      // What each chat holds after catching up, to mark the open one as read
      const caughtUp = {};
      for (const [conversationId, messages] of Object.entries(cached)) {
        const lastId = getLastMessageId(messages);
        if (lastId == null) continue;

        try {
          const missed = await getMessagesAfterRequest(conversationId, lastId);
          if (cancelled) return;

          if (!missed.complete) {
            // Too many to merge: forget this chat so that opening it loads the latest messages again
            const latest = await getConversationMessagesRequest(conversationId);
            if (cancelled) return;
            caughtUp[conversationId] = latest.items;
            setChatMessages((prev) => ({ ...prev, [conversationId]: latest.items }));
            setHistoryPaging((prev) => ({
              ...prev,
              [conversationId]: { hasMore: latest.hasMore, nextBefore: latest.nextBefore, loading: false },
            }));
          } else if (missed.items.length > 0) {
            caughtUp[conversationId] = mergeMessages(messages, missed.items);
            setChatMessages((prev) => ({
              ...prev,
              [conversationId]: mergeMessages(prev[conversationId], missed.items),
            }));
          }
        } catch {
          // This chat stays as it was
        }
      }

      const { activeChatId: openChatId, markConversationAsRead: markAsRead } = catchUpStateRef.current;
      if (openChatId != null) {
        markAsRead(openChatId, caughtUp[String(openChatId)] ?? cached[openChatId]);
      }
    };

    catchUp();

    return () => {
      cancelled = true;
    };
  }, [isAuthenticated, websocketConnected, getLastMessageId]);

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

    // A chat that is not loaded (older page, or new): the first page brings its real name and avatar (T10b)
    if (!chatsRef.current.some((chat) => String(chat.id) === String(conversationId))) {
      refreshChats().catch(() => {});
    }

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
  }, [activeChatId, sendReadReceipt, buildLastMessagePreview, refreshChats]);

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

  const totalUnreadCount = countUnreadChats(chats) + unreadElsewhere;

  return (
    <ConversationsContext.Provider 
      value={{ 
        chats, 
        setChats, 
        chatMessages, 
        setChatMessages,
        loadingChats,
        chatsPaging,
        loadMoreChats,
        errorChats,
        upsertConversation,
        ensurePrivateConversation,
        createGroupConversation,
        loadConversationMessages,
        loadOlderMessages,
        historyPaging,
        historyError,
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