import "./ToggleSwitch.css";

export default function ToggleSwitch({ checked, onChange, label }) {
  return (
    <label className="toggle-switch">
      <input type="checkbox" checked={checked} onChange={onChange} />
      <span className="slider" />
      {label && <span className="label-text">{label}</span>}
    </label>
  );
}
