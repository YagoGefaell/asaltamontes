import SettingsLayout from "../layouts/SettingsLayout.jsx";

function SettingsPage({ title, backTo, children }) {
  return (
    <SettingsLayout title={title} backTo={backTo}>
      {children}
    </SettingsLayout>
  );
}

export default SettingsPage;