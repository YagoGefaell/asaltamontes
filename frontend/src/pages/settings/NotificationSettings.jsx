import "./NotificationSettings.css";
import { useEffect, useState } from "react";
import {
  getNotificationPreferencesRequest,
  updateNotificationPreferencesRequest,
} from "../../features/notifications/services/notifications.service";
import { NOTIFICATION_TYPE_LABELS } from "../../features/notifications/utils/notificationText";
import ToggleSwitch from "../../shared/ui/ToggleSwitch";
import FormError from "../../shared/forms/FormError";

export default function NotificationSettings() {
  const [types, setTypes] = useState(null);
  const [error, setError] = useState("");

  useEffect(() => {
    let cancelled = false;
    getNotificationPreferencesRequest()
      .then((result) => !cancelled && setTypes(result))
      .catch((err) => !cancelled && setError(err.message));
    return () => {
      cancelled = true;
    };
     
  }, []);

  // Optimistic: flips at once and goes back if the server refuses
  const handleToggle = async (type) => {
    const enabled = !types[type];
    setTypes((current) => ({ ...current, [type]: enabled }));
    setError("");
    try {
      await updateNotificationPreferencesRequest({ [type]: enabled });
    } catch (err) {
      setTypes((current) => ({ ...current, [type]: !enabled }));
      setError(err.message || "No se ha podido guardar el cambio");
    }
  };

  if (!types) {
    return error
      ? <FormError message={error} />
      : <p className="notification-settings-hint">Cargando…</p>;
  }

  return (
    <div className="notification-settings">
      <p className="notification-settings-hint">Elige de qué quieres que te avisemos.</p>
      <FormError message={error} />
      <ul>
        {Object.entries(NOTIFICATION_TYPE_LABELS).map(([type, label]) => (
          <li key={type}>
            <ToggleSwitch checked={types[type] !== false} onChange={() => handleToggle(type)} label={label} />
          </li>
        ))}
      </ul>
    </div>
  );
}
