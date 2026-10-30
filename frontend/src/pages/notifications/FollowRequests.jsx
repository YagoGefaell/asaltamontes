import "./FollowRequests.css";
import { useCallback, useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { FaUserLock } from "react-icons/fa";
import { decideFollowRequest, getFollowRequestsRequest } from "../../features/users/services/users.service";
import Button from "../../shared/forms/Button";
import Avatar from "../../shared/ui/Avatar";
import EmptyState from "../../shared/ui/EmptyState";
import PageHeader from "../../shared/ui/PageHeader";

/** Requests to follow my private profile (T23a): accept or reject each one. */
const FollowRequests = () => {
  const navigate = useNavigate();
  const [items, setItems] = useState([]);
  const [hasMore, setHasMore] = useState(false);
  const [page, setPage] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(null);

  const load = useCallback(async (target) => {
    setLoading(true);
    setError("");
    try {
      const result = await getFollowRequestsRequest({ page: target });
      setItems((prev) => (target === 0 ? result.items : [...prev, ...result.items]));
      setHasMore(result.hasMore);
      setPage(target);
    } catch (err) {
      setError(err.message);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    load(0);
  }, [load]);

  const decide = async (username, decision) => {
    setBusy(username);
    setError("");
    try {
      await decideFollowRequest(username, decision);
      setItems((prev) => prev.filter((item) => item.username !== username));
    } catch (err) {
      setError(err.message);
    } finally {
      setBusy(null);
    }
  };

  return (
    <div className="follow-requests-page">
      <PageHeader title="Solicitudes de seguimiento" onBack={() => navigate(-1)} />

      {error && <p className="fr-error" role="alert">{error}</p>}

      {!loading && !error && items.length === 0 && (
        <EmptyState
          icon={<FaUserLock />}
          title="No tienes solicitudes"
          description="Cuando alguien pida seguirte, podrás aceptarla o rechazarla aquí."
        />
      )}

      <ul className="fr-list">
        {items.map((item) => (
          <li key={item.username} className="fr-item">
            <button type="button" className="fr-user" onClick={() => navigate(`/users/${item.username}`)}>
              <Avatar src={item.profilePictureUrl} name={item.fullName || item.username} size="sm" />
              <span className="fr-names">
                <span className="fr-name">{item.fullName || item.username}</span>
                <span className="fr-username">@{item.username}</span>
              </span>
            </button>
            <div className="fr-actions">
              <Button size="small" variant="primary" disabled={busy === item.username} onClick={() => decide(item.username, "approve")}>
                Aceptar
              </Button>
              <Button size="small" variant="outline" disabled={busy === item.username} onClick={() => decide(item.username, "reject")}>
                Rechazar
              </Button>
            </div>
          </li>
        ))}
      </ul>

      {hasMore && (
        <Button variant="ghost" className="fr-more" loading={loading} onClick={() => load(page + 1)}>
          Cargar más
        </Button>
      )}
    </div>
  );
};

export default FollowRequests;
