import { useCallback, useEffect, useState } from "react";
import { useNavigate, useParams, useSearchParams } from "react-router-dom";
import Button from "../../shared/forms/Button";
import PageHeader from "../../shared/ui/PageHeader";
import Skeleton from "../../shared/ui/Skeleton";
import PostCard from "../../features/posts/components/PostCard";
import { getPostRequest } from "../../features/posts/services/posts.service";
import "./PostDetail.css";

/** One publication (T22b). Reached after publishing and from the POST_COMMENT notification, which opens the comments. */
export default function PostDetail() {
  const { id } = useParams();
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const [post, setPost] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(null);

  const load = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      setPost(await getPostRequest(id));
    } catch (requestError) {
      setPost(null);
      setError(requestError);
    } finally {
      setLoading(false);
    }
  }, [id]);

  useEffect(() => { load(); }, [load]);

  const notFound = error?.status === 404 || error?.status === 400;

  return (
    <div className="post-detail">
      <PageHeader title="Publicación" onBack={() => navigate(-1)} backLabel="Volver" />
      {loading && <div className="post-detail__skeleton"><Skeleton height="260px" /></div>}
      {!loading && error && (
        <div className="post-detail__error" role="alert">
          <p>{notFound ? "Esta publicación ya no está disponible." : error.message}</p>
          {!notFound && <Button variant="outline" size="small" onClick={load}>Reintentar</Button>}
        </div>
      )}
      {!loading && post && (
        <PostCard post={post} openComments={searchParams.get("comments") === "1"} onDeleted={() => navigate(-1)} />
      )}
    </div>
  );
}
