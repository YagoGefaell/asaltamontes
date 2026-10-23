// Date helpers for the chat. They use the viewer's own locale and time zone (D-019: no Spain assumptions).

/** The backend sends LocalDateTime-like strings in UTC: add "Z" when there is no offset. */
export function parseMessageDate(dateLike) {
  if (!dateLike) return null;

  const rawValue = String(dateLike).trim();
  const hasTimezone = /[zZ]|[+-]\d{2}:?\d{2}$/.test(rawValue);
  const date = new Date(hasTimezone ? rawValue : `${rawValue}Z`);

  return Number.isNaN(date.getTime()) ? null : date;
}

export function formatMessageTime(dateLike) {
  const date = parseMessageDate(dateLike);
  if (!date) return "";
  return new Intl.DateTimeFormat(undefined, { hour: "2-digit", minute: "2-digit" }).format(date);
}

function startOfDay(date) {
  return new Date(date.getFullYear(), date.getMonth(), date.getDate());
}

export function isSameDay(a, b) {
  return startOfDay(a).getTime() === startOfDay(b).getTime();
}

/** "Hoy", "Ayer" or a long date in the viewer's locale (without year when it is the current one). */
export function formatDayLabel(dateLike, now = new Date()) {
  const date = parseMessageDate(dateLike);
  if (!date) return "";

  const dayDiff = Math.round((startOfDay(now).getTime() - startOfDay(date).getTime()) / 86400000);
  if (dayDiff === 0) return "Hoy";
  if (dayDiff === 1) return "Ayer";

  const options = { weekday: "long", day: "numeric", month: "long" };
  if (date.getFullYear() !== now.getFullYear()) options.year = "numeric";
  return new Intl.DateTimeFormat(undefined, options).format(date);
}
