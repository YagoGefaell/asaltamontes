import "./SectionCard.css";

/** Block with an optional short title, used to group fields in long forms and in settings. */
export default function SectionCard({ title, children, className = "" }) {
  return (
    <section className={["section-card", className].filter(Boolean).join(" ")}>
      {title && <h3 className="section-card__title">{title}</h3>}
      {children}
    </section>
  );
}
