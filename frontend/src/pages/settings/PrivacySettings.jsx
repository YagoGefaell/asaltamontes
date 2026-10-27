import "./PrivacySettings.css";
import { useCallback, useEffect, useState } from "react";
import { useUserProfile } from "../../features/users/hooks/useUserProfile";
import { getBlockedUsersRequest, unblockUserRequest } from "../../features/users/services/users.service";
import Avatar from "../../shared/ui/Avatar";
import { useToast } from "../../shared/ui/Toast";

const DM_POLICY_OPTIONS = [
  { value: "EVERYONE", label: "Cualquier usuaria" },
  { value: "FOLLOWING", label: "Solo usuarias a las que sigo" },
  { value: "MUTUALS", label: "Solo usuarias que nos seguimos mutuamente" },
  { value: "NOBODY", label: "Nadie" },
];

export default function PrivacySettings() {
  const { userProfile, updateUserProfile } = useUserProfile();
  const toast = useToast();
  const [blocked, setBlocked] = useState([]);
  const [hasMore, setHasMore] = useState(false);
  const [page, setPage] = useState(0);
  const [loading, setLoading] = useState(true);

  const load = useCallback(async (pageToLoad) => {
    setLoading(true);
    try {
      const result = await getBlockedUsersRequest(pageToLoad);
      setBlocked((current) => (pageToLoad === 0 ? result.content : [...current, ...result.content]));
      setHasMore(!result.last);
      setPage(pageToLoad);
    } catch (error) {
      toast.error(error.message);
    } finally {
      setLoading(false);
    }
  }, [toast]);

  useEffect(() => {
    load(0);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const handlePolicyChange = async (event) => {
    const updated = await updateUserProfile({ dmPolicy: event.target.value });
    if (updated) {
      toast.success("Preferencia guardada");
    } else {
      toast.error("No se ha podido guardar la preferencia");
    }
  };

  const handleUnblock = async (username) => {
    if (!window.confirm(`¿Desbloquear a @${username}?`)) return;
    try {
      await unblockUserRequest(username);
      setBlocked((current) => current.filter((user) => user.username !== username));
    } catch (error) {
      toast.error(error.message);
    }
  };

  return (
    <div className="privacy-settings">
      <section>
        <h3>Quién puede escribirme</h3>
        <p className="privacy-hint">
          Solo afecta a los chats nuevos: los que ya tienes siguen funcionando.
        </p>
        <select
          aria-label="Quién puede escribirme"
          value={userProfile?.dmPolicy || "EVERYONE"}
          onChange={handlePolicyChange}
        >
          {DM_POLICY_OPTIONS.map((option) => (
            <option key={option.value} value={option.value}>{option.label}</option>
          ))}
        </select>
      </section>

      <section>
        <h3>Usuarias bloqueadas</h3>
        {!loading && blocked.length === 0 && (
          <p className="privacy-hint">No has bloqueado a nadie.</p>
        )}
        <ul className="blocked-list">
          {blocked.map((user) => (
            <li key={user.username}>
              <Avatar src={user.profilePictureUrl} name={user.fullName || user.username} size="sm" />
              <span className="blocked-name">@{user.username}</span>
              <button type="button" onClick={() => handleUnblock(user.username)}>Desbloquear</button>
            </li>
          ))}
        </ul>
        {hasMore && (
          <button type="button" disabled={loading} onClick={() => load(page + 1)}>Ver más</button>
        )}
      </section>
    </div>
  );
}
