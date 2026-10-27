import { useCallback } from "react";
import Button from "../../../shared/forms/Button";
import EmptyState from "../../../shared/ui/EmptyState";
import FormError from "../../../shared/forms/FormError";
import Skeleton from "../../../shared/ui/Skeleton";
import { useCursorList } from "../../../features/communities/hooks/useCursorList";
import { getModerationLogRequest } from "../../../features/communities/services/communities.service";
import { describeEntry } from "../../../features/communities/utils/moderationLog";
import { communityErrorMessage } from "../../../features/communities/utils/communityErrors";
import "./CommunityModeration.css";

export default function ModerationLog({ community }) {
  const fetchPage = useCallback((before) => getModerationLogRequest(community.id, before), [community.id]);
  const list = useCursorList(fetchPage);

  if (list.loading) return <Skeleton width="100%" height="4rem" />;
  if (list.error) return <FormError message={communityErrorMessage(list.error)} />;
  if (list.items.length === 0) {
    return <EmptyState title="Sin actividad de moderación" description="Las acciones de la moderación quedarán registradas aquí." />;
  }

  return (
    <>
      <ul className="moderation-list">
        {list.items.map((entry) => {
          const { text, detail } = describeEntry(entry);
          return (
            <li key={entry.id} className="moderation-item moderation-item--log">
              <div className="moderation-item__info">
                <span>{text}</span>
                {detail && <span className="moderation-item__reason">{detail}</span>}
                <time className="moderation-item__meta" dateTime={entry.createdAt}>
                  {new Date(entry.createdAt).toLocaleString("es-ES", { dateStyle: "medium", timeStyle: "short" })}
                </time>
              </div>
            </li>
          );
        })}
      </ul>
      {list.hasMore && (
        <Button variant="secondary" onClick={list.loadMore} loading={list.loadingMore}>Cargar más</Button>
      )}
    </>
  );
}
