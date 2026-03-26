import { useState, useCallback, useEffect } from "react";
import { useAuth } from "../../auth/hooks/useAuth"; 
import { useGlobalMessageListener } from "../hooks/useGlobalMessageListener";
import { ConversationsContext } from "../hooks/conversations.context";
import {
  getMyConversationsRequest,
  getConversationMessagesRequest,
  createOrGetPrivateConversationRequest,
} from "../services/messages.service.js";
import { getUserByUsername } from "../../users/services/users.service.js";

export const ConversationsProvider = ({ children }) => {
  const { websocketClient, websocketConnected, isAuthenticated } = useAuth();

  const [chats, setChats] = useState([]);
  const [chatMessages, setChatMessages] = useState({});
  const [loadingChats, setLoadingChats] = useState(false);
  const [errorChats, setErrorChats] = useState(null);

  const [activeChatId, setActiveChatId] = useState(null);

  const mapConversationToChat = useCallback((conversation) => {
    const username = conversation?.contactUsername || "usuario";
    const firstChar = username.trim().charAt(0).toUpperCase();
    const lastMessagePreview = (conversation?.lastMessage || "").trim();

    return {
      id: conversation?.conversationId,
      name: username,
      avatar: firstChar || "?",
      avatarUrl: null,
      unreadCount: Number(conversation?.unreadCount || 0),
      lastMsg: lastMessagePreview || "Abre la conversacion",
      lastMessageAt: conversation?.lastMessageAt || null,
    };
  }, []);

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

    const createdConversation = await createOrGetPrivateConversationRequest(recipientUsername);
    const createdConversationId = createdConversation?.conversationId;

    if (!createdConversationId) {
      throw new Error("No se pudo crear la conversacion");
    }

    const mappedChat = {
      id: createdConversationId,
      name: createdConversation?.contactUsername || recipientUsername,
      avatar: chat?.avatar || recipientUsername.trim().charAt(0).toUpperCase() || "?",
      avatarUrl: chat?.avatarUrl || null,
      unreadCount: 0,
      lastMsg: chat?.lastMsg || "Abre la conversacion",
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
  }, []);

  const loadConversationMessages = useCallback(async (conversationId) => {
    if (!conversationId) return;

    const alreadyLoaded = chatMessages[conversationId];
    if (alreadyLoaded) {
      setChats((prevChats) => prevChats.map((chat) => (
        String(chat.id) === String(conversationId)
          ? { ...chat, unreadCount: 0 }
          : chat
      )));
      return;
    }

    try {
      const messages = await getConversationMessagesRequest(conversationId);
      setChatMessages((prev) => ({
        ...prev,
        [conversationId]: Array.isArray(messages) ? messages : [],
      }));
      setChats((prevChats) => prevChats.map((chat) => (
        String(chat.id) === String(conversationId)
          ? { ...chat, unreadCount: 0 }
          : chat
      )));
    } catch {
      // No bloqueamos la UI del chat si falla la carga inicial del historial.
    }
  }, [chatMessages]);

  useEffect(() => {
    if (!isAuthenticated) {
      setChats([]);
      setChatMessages({});
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
          ? await Promise.all(conversations.map(async (conversation) => {
              const baseChat = mapConversationToChat(conversation);

              try {
                const profile = await getUserByUsername(baseChat.name);
                return {
                  ...baseChat,
                  avatarUrl: profile?.profilePictureUrl || null,
                };
              } catch {
                return baseChat;
              }
            }))
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

    setChatMessages((prev) => {
      const previous = prev[conversationId] || [];
      return {
        ...prev,
        [conversationId]: [
          ...previous,
          {
            id: payload.id || `ws-${Date.now()}-${Math.random()}`,
            content: payload.content,
            senderUsername: payload.senderUsername,
          },
        ],
      };
    });

    setChats((prevChats) => {
      const existing = prevChats.find(
        (chat) => String(chat.id) === String(conversationId)
      );

      if (!existing) {
        const fallbackUsername = payload?.senderUsername || payload?.receiverUsername || "usuario";
        const firstChar = fallbackUsername.trim().charAt(0).toUpperCase();
        const isCurrentlyReading = String(activeChatId) === String(conversationId);

        return [
          {
            id: conversationId,
            name: fallbackUsername,
            avatar: firstChar || "?",
            avatarUrl: null,
            unreadCount: isCurrentlyReading ? 0 : 1,
            lastMsg: payload.content,
          },
          ...prevChats,
        ];
      }

      return prevChats.map(chat => {
        if (String(chat.id) === String(conversationId)) {
          const isCurrentlyReading = String(activeChatId) === String(conversationId);
          const newUnreadCount = isCurrentlyReading ? 0 : (chat.unreadCount || 0) + 1;

          return {
            ...chat,
            lastMsg: payload.content,
            unreadCount: newUnreadCount
          };
        }
        return chat;
      });
    });
  }, [activeChatId]);

  useGlobalMessageListener(
    websocketClient,
    websocketConnected,
    handleNewMessage
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
        loadConversationMessages,
        activeChatId,
        setActiveChatId,
        totalUnreadCount
      }}
    >
      {children}
    </ConversationsContext.Provider>
  );
};