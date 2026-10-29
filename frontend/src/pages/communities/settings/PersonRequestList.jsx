import { useCallback, useState } from "react";
import Avatar from "../../../shared/ui/Avatar";
import Button from "../../../shared/forms/Button";
import EmptyState from "../../../shared/ui/EmptyState";
import FormError from "../../../shared/forms/FormError";
import Skeleton from "../../../shared/ui/Skeleton";
import { useCursorList } from "../../../features/communities/hooks/useCursorList";
import {
  getBannedRequest,
  getJoinRequestsRequest,
  unbanMemberRequest,
  approveJoinRequest,
  rejectJoinRequest,
} from "../../../features/communities/services/communities.service";
import { communityErrorMessage } from "../../../features/communities/utils/communityErrors";
import "./CommunityModeration.css";

const formatDate = (iso) => (iso ? new Date(iso).toLocaleString("es-ES", { dateStyle: "medium", timeStyle: "short" }) : "");

function useRowAction(communityId, removeItem) {
  const [busy, setBusy] = useState(null);
  const [error, setError] = useState("");
  const run = async (username, action) => {
    setBusy(username);
    setError("");
    try {
      await action(communityId, username);
      removeItem((item) => item.username === username);
    } catch (err) {
      setError(communityErrorMessage(err));
      // 404 on a request someone else already decided: the row is stale, drop it
      if (err.status === 404) removeItem((item) => item.username === username);
    } finally {
      setBusy(null);
    }
  };
  return { busy, run, error };
}

function ListShell({ list, empty, error, children }) {
  if (list.loading) return <Skeleton width="100%" height="4rem" />;
  if (list.error) return <FormError message={communityErrorMessage(list.error)} />;
  if (list.items.length === 0) return <EmptyState title={empty.title} description={empty.description} />;
  return (
    <>
      <FormError message={error} />
      <ul className="moderation-list">{children}</ul>
      {list.hasMore && (
        <Button variant="secondary" onClick={list.loadMore} loading={list.loadingMore}>Cargar más</Button>
      )}
    </>
  );
}

/** "Expulsadas": who, when, why and who expelled her, with "Readmitir" (staff only). */
export function BannedList({ community }) {
  const fetchPage = useCallback((before) => getBannedRequest(community.id, before), [community.id]);
  const list = useCursorList(fetchPage);
  const { busy, run, error } = useRowAction(community.id, list.removeItem);

  return (
    <ListShell list={list} error={error} empty={{ title: "No hay expulsadas", description: "Las usuarias expulsadas aparecerán aquí." }}>
      {list.items.map((person) => (
        <li key={person.id || person.username} className="moderation-item">
          <Avatar src={person.profilePictureUrl} name={person.fullName || person.username} size="md" />
          <div className="moderation-item__info">
            <strong>{person.fullName || person.username}</strong>
            <span className="moderation-item__meta">@{person.username}</span>
            <span className="moderation-item__meta">
              Expulsada el {formatDate(person.bannedAt)}{person.bannedBy ? ` por @${person.bannedBy}` : ""}
            </span>
            {person.reason && <span className="moderation-item__reason">Motivo: {person.reason}</span>}
          </div>
          <div className="moderation-item__actions">
            <Button
              variant="outline"
              size="small"
              disabled={busy === person.username}
              onClick={() => run(person.username, unbanMemberRequest)}
            >
              Readmitir
            </Button>
          </div>
        </li>
      ))}
    </ListShell>
  );
}

/** Pending requests to join a private community, with accept / reject. */
export function JoinRequestList({ community }) {
  const fetchPage = useCallback((before) => getJoinRequestsRequest(community.id, before), [community.id]);
  const list = useCursorList(fetchPage);
  const { busy, run, error } = useRowAction(community.id, list.removeItem);

  return (
    <ListShell list={list} error={error} empty={{ title: "No hay solicitudes pendientes", description: "Cuando alguien pida unirse, la verás aquí." }}>
      {list.items.map((person) => (
        <li key={person.id || person.username} className="moderation-item">
          <Avatar src={person.profilePictureUrl} name={person.fullName || person.username} size="md" />
          <div className="moderation-item__info">
            <strong>{person.fullName || person.username}</strong>
            <span className="moderation-item__meta">@{person.username}</span>
            <span className="moderation-item__meta">Solicitud del {formatDate(person.requestedAt)}</span>
          </div>
          <div className="moderation-item__actions">
            <Button
              size="small"
              disabled={busy === person.username}
              onClick={() => run(person.username, approveJoinRequest)}
            >
              Aceptar
            </Button>
            <Button
              variant="outline"
              size="small"
              disabled={busy === person.username}
              onClick={() => run(person.username, rejectJoinRequest)}
            >
              Rechazar
            </Button>
          </div>
        </li>
      ))}
    </ListShell>
  );
}
