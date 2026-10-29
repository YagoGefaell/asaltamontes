import "./Communities.css";
import { useCallback, useEffect, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { FaChevronRight, FaPlus, FaRegComments } from "react-icons/fa";
import { useSearchSection } from "../../features/search/hooks/useSearchSection";
import {
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

const SKELETON_ROWS = 4;
const SUGGESTIONS = 8;

/** "sáb 31": day of the week and of the month, in the event's time zone. */
const formatEventDay = (event) =>
  new Intl.DateTimeFormat("es-ES", {
    weekday: "short", day: "numeric", timeZone: event.timezone || undefined,
  }).format(new Date(event.startsAt)).replace(".", "");

const membersLabel = (count) => `${count} ${count === 1 ? "miembro" : "miembros"}`;

/** Short time of the last message: the hour today, the day otherwise. */
function formatActivity(dateLike) {
  const date = parseMessageDate(dateLike);
  if (!date) return "";
  if (date.toDateString() === new Date().toDateString()) return formatMessageTime(dateLike);
  return new Intl.DateTimeFormat("es-ES", { day: "numeric", month: "short" }).format(date);
}

/** Last message if there is one; otherwise "8 miembros · Próxima quedada sáb 31". */
function rowSubtitle({ chat, memberCount, nextEvent }) {
  if (chat?.lastMessageAt) return chatPreview(chat);
  return [membersLabel(memberCount ?? 0), nextEvent && `Próxima quedada ${formatEventDay(nextEvent)}`].filter(Boolean).join(" · ");
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
  const { chat } = community;
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
            {rowSubtitle(community)}
          </span>
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

/** Not-yet-member communities: first those of her city, then the biggest ones. */
async function fetchSuggested(city) {
  const bySize = { sort: "memberCount,desc" };
  const [local, all] = await Promise.all([
    city ? getCommunitiesRequest({ city, ...bySize }).catch(() => ({ items: [] })) : { items: [] },
    getCommunitiesRequest(bySize),
  ]);
  const seen = new Set();
  const items = [...local.items, ...all.items].filter((c) => {
    if (c.role || seen.has(c.id)) return false;
    seen.add(c.id);
    return true;
  });
  return { items: items.slice(0, SUGGESTIONS), hasMore: false };
}

function Suggested({ city }) {
  const fetchPage = useCallback(() => fetchSuggested(city), [city]);
  const { items } = useSearchSection(fetchPage, city);
  if (items.length === 0) return null;
  return (
    <section className="communities-section communities-section--rail" aria-label="Sugeridas para ti">
      <h2 className="communities-section__title">Sugeridas para ti</h2>
      <ul className="suggested-rail">
        {items.map((community) => (
          <li key={community.id} className="suggested-rail__item">
            <Link to={communityPath(community)} className="suggested-rail__link">
              <Avatar src={community.imageUrl} name={community.name} size="lg" />
              <span className="suggested-rail__name">{community.name}</span>
              <span className="suggested-rail__meta">
                {[community.city, membersLabel(community.memberCount ?? 0)].filter(Boolean).join(" · ")}
              </span>
            </Link>
            {community.visibility === "PRIVATE" ? (
              <CommunityRequestButton communityId={community.id} />
            ) : (
              <CommunityJoinButton communityId={community.id} role={community.role} joinLabel="Unirse" memberLabel="Miembro" />
            )}
          </li>
        ))}
      </ul>
    </section>
  );
}

const Communities = () => {
  const navigate = useNavigate();
  const { userProfile } = useUserProfile();
  const mine = useSearchSection(useCallback((page) => getMyCommunitiesRequest({ page }), []), "mine");
  const [pending, setPending] = useState({ toApprove: [], mine: [] });

  // Lo pendiente es un extra: si falla, la pantalla sigue mostrando las comunidades
  useEffect(() => {
    let cancelled = false;
    getPendingCommunitiesRequest()
      .then((result) => { if (!cancelled) setPending(result); })
      .catch(() => {});
    return () => { cancelled = true; };
  }, []);

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

  const empty = !mine.loading && !mine.error && mine.items.length === 0;

  return (
    <div className="communities-page">
      <PageHeader
        title="Comunidades"
        actions={
          <Link to="/communities/new" aria-label="Crear comunidad" title="Crear comunidad">
            <FaPlus aria-hidden="true" />
          </Link>
        }
      />

      <PendingSection pending={pending} />

      <section className="communities-section" aria-label="Tus comunidades" aria-busy={mine.loading || undefined}>

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
          action={<Button onClick={() => navigate("/communities/new")}>Crear comunidad</Button>}
        />
      )}
      <Suggested city={userProfile?.city?.trim() || ""} />
    </div>
  );
};

export default Communities;
