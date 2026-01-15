const API_URL = import.meta.env.VITE_API_URL || "http://192.168.1.42:8080/auth";

// ---------------- LOGIN ----------------
export async function loginRequest({ username, password }) {
  const res = await fetch(`${API_URL}/login`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    credentials: "include", // muy importante: enviar cookies
    body: JSON.stringify({ username, password }),
  });

  if (!res.ok) throw new Error("Usuario o contraseña incorrectos");
  return; // ya no hay accessToken/refreshToken en body
}

// ---------------- REGISTER ----------------
export async function registerRequest({ username, email, password }) {
  const res = await fetch(`${API_URL}/register`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    credentials: "include",
    body: JSON.stringify({ username, email, password }),
  });

  if (!res.ok) {
    const errorData = await res.json();
    throw errorData;
  }
  return; // tokens ya están en cookies
}

// ---------------- LOGOUT ----------------
export async function logoutRequest() {
  const res = await fetch(`${API_URL}/logout`, {
    method: "POST",
    credentials: "include", // enviar cookies para que se borren
  });

  if (!res.ok) throw new Error("Error al cerrar sesión");
  return;
}

// ---------------- VERIFY SESSION ----------------
export async function verifySessionRequest() {
  const res = await fetch(`${API_URL}/verify`, {
    method: "GET",
    credentials: "include",
  });

  if (!res.ok) throw new Error("Error al verificar la sesión");
  return;
}