import "./Communities.css";
import { useCallback, useEffect, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { FaChevronRight, FaPlus, FaRegComments } from "react-icons/fa";
import { useSearchSection } from "../../features/search/hooks/useSearchSection";
import {
  createCommunityRequest,
  getCommunitiesRequest,
  getMyCommunitiesRequest,
  getPendingCommunitiesRequest,
} from "../../features/communities/services/communities.service.js";
import { communityPath } from "../../features/communities/utils/communityPath.js";
import { parseMessageDate, formatMessageTime } from "../../features/messages/utils/messageDates";
import { useUserProfile } from "../../features/users/hooks/useUserProfile";
import CommunityJoinButton from "../../shared/components/CommunityJoinButton";
import CommunityRequestButton from "../../shared/components/CommunityRequestButton";
import Button from "../../shared/forms/Button";
import PageHeader from "../../shared/ui/PageHeader";
import EmptyState from "../../shared/ui/EmptyState";
import Avatar from "../../shared/ui/Avatar";
import Skeleton from "../../shared/ui/Skeleton";
import { useToast } from "../../shared/ui/Toast";
import CommunityCreatorPanel from "./components/CommunityCreatorPanel";

const SKELETON_ROWS = 4;
const SUGGESTIONS = 5;

const formatEventDate = (event) =>
  new Intl.DateTimeFormat("es-ES", {
    weekday: "short", day: "numeric", month: "short", hour: "2-digit", minute: "2-digit",
    timeZone: event.timezone || undefined,
  }).format(new Date(event.startsAt));

/** Short time of the last message: the hour today, the day otherwise. */
function formatActivity(dateLike) {
  const date = parseMessageDate(dateLike);
  if (!date) return "";
  if (date.toDateString() === new Date().toDateString()) return formatMessageTime(dateLike);
  return new Intl.DateTimeFormat("es-ES", { day: "numeric", month: "short" }).format(date);
}

function chatPreview(chat) {
  if (!chat?.lastMessageAt) return "Todavía no hay mensajes";
  // No text: the last message is from someone blocked (D-022)
  if (chat.lastMessage == null) return "Mensaje de una usuaria bloqueada";
  return chat.lastSender ? `${chat.lastSender}: ${chat.lastMessage}` : chat.lastMessage;
}

function PendingSection({ pending }) {
  const { toApprove, mine } = pending;
  if (toApprove.length === 0 && mine.length === 0) return null;
  return (
    <section className="communities-section" aria-label="Pendiente">
      <h2 className="communities-section__title">Pendiente</h2>
      <ul className="communities-pending">
        {toApprove.map(({ community, pendingCount }) => (
          <li key={`approve-${community.id}`}>
            <Link to={communityPath(community, "/settings/requests")} className="communities-pending__row">
              <Avatar src={community.imageUrl} name={community.name} size="md" />
              <span className="communities-pending__text">
                <span className="community-row__name">{community.name}</span>
                <span className="community-members">
                  {pendingCount === 1 ? "1 solicitud por aprobar" : `${pendingCount} solicitudes por aprobar`}
                </span>
              </span>
              <FaChevronRight aria-hidden="true" className="communities-pending__chevron" />
            </Link>
          </li>
        ))}
        {mine.map(({ id, community }) => (
          <li key={`mine-${id}`}>
            <Link to={communityPath(community)} className="communities-pending__row">
              <Avatar src={community.imageUrl} name={community.name} size="md" />
              <span className="communities-pending__text">
                <span className="community-row__name">{community.name}</span>
                <span className="community-members">Tu solicitud está esperando respuesta</span>
              </span>
              <FaChevronRight aria-hidden="true" className="communities-pending__chevron" />
            </Link>
          </li>
        ))}
      </ul>
    </section>
  );
}

function MyCommunityRow({ community, onOpenChat }) {
  const { chat, nextEvent } = community;
  const unread = chat?.unreadCount ?? 0;
  return (
    <li className="my-community">
      <Link to={communityPath(community)} className="my-community__link">
        <Avatar src={community.imageUrl} name={community.name} size="lg" />
        <span className="my-community__text">
          <span className="my-community__top">
            <span className="community-row__name">{community.name}</span>
            <span className="my-community__time">{formatActivity(chat?.lastMessageAt)}</span>
          </span>
          <span className={`my-community__preview${unread > 0 ? " my-community__preview--unread" : ""}`}>
            {chatPreview(chat)}
          </span>
          {nextEvent && (
            <span className="my-community__event">Próxima quedada · {formatEventDate(nextEvent)}</span>
          )}
        </span>
      </Link>
      {chat?.conversationId && (
        <button
          type="button"
          className="my-community__chat"
          onClick={() => onOpenChat(community)}
          aria-label={unread > 0 ? `Abrir el chat de ${community.name}, ${unread} sin leer` : `Abrir el chat de ${community.name}`}
        >
          <FaRegComments aria-hidden="true" />
          {unread > 0 && <span className="my-community__badge" aria-hidden="true">{unread > 9 ? "9+" : unread}</span>}
        </button>
      )}
    </li>
  );
}

function Suggestions({ city }) {
  const fetchPage = useCallback(async () => {
    const result = await getCommunitiesRequest({ city });
    return { items: result.items.filter((c) => !c.role).slice(0, SUGGESTIONS), hasMore: false };
  }, [city]);
  const { items } = useSearchSection(fetchPage, city, !!city);
  if (items.length === 0) return null;
  return (
    <section className="communities-section" aria-label={`Comunidades en ${city}`}>
      <h2 className="communities-section__title">En {city}</h2>
      <div className="communities-list">
        {items.map((community) => (
          <div key={community.id} className="community-row">
            <Link to={communityPath(community)} className="community-row__link">
              <Avatar src={community.imageUrl} name={community.name} size="lg" />
              <span className="community-row__text">
                <span className="community-row__name">{community.name}</span>
                <span className="community-members">
                  {[community.city, `${community.memberCount} ${community.memberCount === 1 ? "miembro" : "miembros"}`].filter(Boolean).join(" · ")}
                </span>
              </span>
            </Link>
            <div className="community-row__action">
              {community.visibility === "PRIVATE" ? (
                <CommunityRequestButton communityId={community.id} />
              ) : (
                <CommunityJoinButton communityId={community.id} role={community.role} joinLabel="Unirse" memberLabel="Miembro" />
              )}
            </div>
          </div>
        ))}
      </div>
    </section>
  );
}

const Communities = () => {
  const toast = useToast();
  const navigate = useNavigate();
  const { userProfile } = useUserProfile();
  const mine = useSearchSection(useCallback((page) => getMyCommunitiesRequest({ page }), []), "mine");
  const [pending, setPending] = useState({ toApprove: [], mine: [] });
  const [isCreatorOpen, setIsCreatorOpen] = useState(false);
  const [creatingCommunity, setCreatingCommunity] = useState(false);

  // Lo pendiente es un extra: si falla, la pantalla sigue mostrando las comunidades
  useEffect(() => {
    let cancelled = false;
    getPendingCommunitiesRequest()
      .then((result) => { if (!cancelled) setPending(result); })
      .catch(() => {});
    return () => { cancelled = true; };
  }, []);

  const openCreator = useCallback(() => setIsCreatorOpen(true), []);
  const closeCreator = useCallback(() => {
    if (creatingCommunity) return;
    setIsCreatorOpen(false);
  }, [creatingCommunity]);

  const handleCreateCommunity = useCallback(
    async (communityData) => {
      if (creatingCommunity) return;
      setCreatingCommunity(true);
      try {
        await createCommunityRequest(communityData);
        setIsCreatorOpen(false);
        toast.success("Comunidad creada");
        await mine.retry();
      } finally {
        setCreatingCommunity(false);
      }
    },
    [creatingCommunity, mine, toast]
  );

  const openChat = useCallback(
    (community) => {
      navigate("/messages", {
        state: {
          selectedConversation: {
            conversationId: community.chat.conversationId,
            type: "GROUP",
            displayName: community.name,
            displayImage: community.imageUrl || null,
          },
        },
      });
    },
    [navigate]
  );

  if (isCreatorOpen) {
    return (
      <CommunityCreatorPanel
        isOpen={isCreatorOpen}
        creatingCommunity={creatingCommunity}
        onClose={closeCreator}
        onSubmitCommunity={handleCreateCommunity}
      />
    );
  }

  const empty = !mine.loading && !mine.error && mine.items.length === 0;

  return (
    <div className="communities-page">
      <PageHeader
        title="Comunidades"
        actions={
          <button type="button" aria-label="Crear comunidad" title="Crear comunidad" onClick={openCreator} disabled={creatingCommunity}>
            <FaPlus aria-hidden="true" />
          </button>
        }
      />

      <PendingSection pending={pending} />

      <section className="communities-section" aria-label="Tus comunidades" aria-busy={mine.loading || undefined}>
        {!empty && <h2 className="communities-section__title">Tus comunidades</h2>}

        {mine.loading && (
          <ul className="my-communities">
            {Array.from({ length: SKELETON_ROWS }, (_, i) => (
              <li key={i} className="my-community" data-testid="community-skeleton">
                <Skeleton width="56px" height="56px" circle />
                <div className="my-community__text">
                  <Skeleton width="55%" height="1rem" />
                  <Skeleton width="80%" height="0.75rem" />
                </div>
              </li>
            ))}
          </ul>
        )}

        {!mine.loading && mine.error && (
          <div className="communities-error" role="alert">
            <p>{mine.error}</p>
            <Button variant="secondary" onClick={mine.retry}>Reintentar</Button>
          </div>
        )}

        {!mine.loading && !mine.error && mine.items.length > 0 && (
          <ul className="my-communities">
            {mine.items.map((community) => (
              <MyCommunityRow key={community.id} community={community} onOpenChat={openChat} />
            ))}
          </ul>
        )}

        {!mine.loading && !mine.error && mine.hasMore && (
          <Button variant="ghost" className="communities-load-more" onClick={mine.loadMore} loading={mine.loadingMore}>
            Cargar más
          </Button>
        )}
      </section>

      {empty && (
        <EmptyState
          className="communities-empty"
          title="Aún no estás en ninguna comunidad"
          description="Únete a una cercana o crea la tuya y reúne a tu grupo."
          action={<Button onClick={openCreator}>Crear comunidad</Button>}
        />
      )}
      {empty && <Suggestions city={userProfile?.city?.trim() || ""} />}

      {!mine.loading && !mine.error && (
        <Link to="/search?tab=communities" className="communities-discover">
          Descubrir comunidades cerca de ti
          <FaChevronRight aria-hidden="true" />
        </Link>
      )}
    </div>
  );
};

export default Communities;
