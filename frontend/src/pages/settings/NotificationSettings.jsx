import "./NotificationSettings.css";
import { useEffect, useState } from "react";
import {
  getNotificationPreferencesRequest,
  updateNotificationPreferencesRequest,
} from "../../features/notifications/services/notifications.service";
import { NOTIFICATION_TYPE_LABELS } from "../../features/notifications/utils/notificationText";
import ToggleSwitch from "../../shared/ui/ToggleSwitch";
import { useToast } from "../../shared/ui/Toast";

export default function NotificationSettings() {
  const toast = useToast();
  const [types, setTypes] = useState(null);

  useEffect(() => {
    let cancelled = false;
    getNotificationPreferencesRequest()
      .then((result) => !cancelled && setTypes(result))
      .catch((error) => !cancelled && toast.error(error.message));
    return () => {
      cancelled = true;
    };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // Optimistic: flips at once and goes back if the server refuses
  const handleToggle = async (type) => {
    const enabled = !types[type];
    setTypes((current) => ({ ...current, [type]: enabled }));
    try {
      await updateNotificationPreferencesRequest({ [type]: enabled });
    } catch (error) {
      setTypes((current) => ({ ...current, [type]: !enabled }));
      toast.error(error.message || "No se ha podido guardar el cambio");
    }
  };

  if (!types) return <p className="notification-settings-hint">Cargando…</p>;

  return (
    <div className="notification-settings">
      <p className="notification-settings-hint">Elige de qué quieres que te avisemos.</p>
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
