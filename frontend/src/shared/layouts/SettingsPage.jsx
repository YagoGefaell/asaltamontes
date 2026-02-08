import SettingsLayout from "./SettingsLayout.jsx";

function SettingsPage({ title, backTo, children }) {
  return (
    <SettingsLayout title={title} backTo={backTo}>
      {children}
    </SettingsLayout>
  );
}

export default SettingsPage;