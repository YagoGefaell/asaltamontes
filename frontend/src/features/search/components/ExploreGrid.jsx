import { Link } from "react-router-dom";
import { FaImages } from "react-icons/fa";
import Button from "../../../shared/forms/Button";
import EmptyState from "../../../shared/ui/EmptyState";
import Skeleton from "../../../shared/ui/Skeleton";
import { useCursorList } from "../../communities/hooks/useCursorList";
import { getExplorePostsRequest } from "../../posts/services/posts.service";
import { useLoadMoreOnView } from "../../posts/hooks/useLoadMoreOnView";
import "./ExploreGrid.css";

/** Search without text (T22c): 3-column grid of photo posts to discover; each tile opens the publication. */
export default function ExploreGrid() {
  const { items, loading, loadingMore, error, hasMore, loadMore, reload } = useCursorList(getExplorePostsRequest);
  const sentinelRef = useLoadMoreOnView(loadMore, hasMore && !loadingMore);

  if (loading) {
    return (
      <div className="explore-grid" data-testid="explore-skeleton" aria-busy="true">
        {Array.from({ length: 9 }, (_, i) => (
          <div key={i} className="explore-grid__tile"><Skeleton height="100%" /></div>
        ))}
      </div>
    );
  }
  if (error) {
    return (
      <div className="explore-error" role="alert">
        <p>{error.message || "No se han podido cargar las publicaciones"}</p>
        <Button variant="outline" size="small" onClick={reload}>Reintentar</Button>
      </div>
    );
  }
  if (items.length === 0) {
    return <EmptyState icon={<FaImages />} title="Aún no hay nada que descubrir" description="Cuando haya publicaciones con foto, las verás aquí." />;
  }

  return (
    <>
      <ul className="explore-grid" aria-label="Publicaciones para descubrir">
        {items.map((post) => (
          <li key={post.id} className="explore-grid__tile">
            <Link to={`/posts/${post.id}`} className="explore-grid__link" aria-label={`Publicación de ${post.author.fullName || post.author.username}`}>
              <img src={post.media[0]?.thumb} alt="" loading="lazy" />
            </Link>
          </li>
        ))}
      </ul>
      {hasMore && (
        <div ref={sentinelRef} className="explore-more">
          <Button variant="ghost" onClick={loadMore} loading={loadingMore}>Cargar más</Button>
        </div>
      )}
    </>
  );
}
