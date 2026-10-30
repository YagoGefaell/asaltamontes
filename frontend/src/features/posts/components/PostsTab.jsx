import { FaPen } from "react-icons/fa";
import Button from "../../../shared/forms/Button";
import EmptyState from "../../../shared/ui/EmptyState";
import Skeleton from "../../../shared/ui/Skeleton";
import { useCursorList } from "../../communities/hooks/useCursorList";
import PostCard from "./PostCard";

/**
 * «Publicaciones» tab of a profile or a community (T22b): the full publications as a list, with «Cargar más».
 * `fetchPage(before)` -> { items, nextBefore } must be stable (useCallback) or the list reloads on every render.
 */
export default function PostsTab({ panelId, tabId, fetchPage, emptyTitle, emptyDescription, emptyAction }) {
  const { items, loading, loadingMore, error, hasMore, loadMore, reload, removeItem } = useCursorList(fetchPage);

  return (
    <div className="pf-panel" role="tabpanel" id={panelId} aria-labelledby={tabId} aria-busy={loading || undefined}>
      {loading && (
        <div className="pf-skeletons" data-testid="posts-skeleton">
          <Skeleton height="220px" />
          <Skeleton height="220px" />
        </div>
      )}
      {!loading && error && (
        <div className="pf-error" role="alert">
          <p>{error.message || "No se han podido cargar las publicaciones"}</p>
          <Button variant="outline" size="small" onClick={reload}>Reintentar</Button>
        </div>
      )}
      {!loading && !error && items.length === 0 && (
        <EmptyState icon={<FaPen />} title={emptyTitle} description={emptyDescription} action={emptyAction} />
      )}
      {!loading && !error && items.map((post) => (
        <PostCard key={post.id} post={post} onDeleted={(deleted) => removeItem((item) => item.id === deleted.id)} />
      ))}
      {!loading && !error && hasMore && (
        <Button variant="ghost" className="pf-more" onClick={loadMore} loading={loadingMore}>Cargar más</Button>
      )}
    </div>
  );
}
