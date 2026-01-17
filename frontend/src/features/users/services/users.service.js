const API_URL = import.meta.env.VITE_API_URL || "http://localhost:8080/api/users";

// ---------------- LOAD USER INFORMATION ----------------
export async function getMeRequest() {
  const res = await fetch(`${API_URL}/me`, {
    method: "GET",
    credentials: "include",
  });
  if (!res.ok) throw new Error("Error al cargar la información del usuario");
  const userData = await res.json();
  return userData;
}

// ---------------- UPDATE USER PROFILE ----------------
export async function updateUserProfileRequest(profileData) {
  const response = await fetch(`${API_URL}/me`, {
    method: "PATCH",
    headers: { "Content-Type": "application/json" },
    credentials: "include",
    body: JSON.stringify(profileData),
  });

  if (!response.ok) {
    const errorText = await response.text();
    throw new Error(errorText || "Error actualizando perfil");
  }

  const text = await response.text();
  return text ? JSON.parse(text) : null;
}
