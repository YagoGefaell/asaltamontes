const API_URL = import.meta.env.VITE_API_URL || "http://localhost:8080";

// ---------------- LOGIN ----------------
export async function loginRequest({ username, password }) {
  try {
    const res = await fetch(`${API_URL}/auth/login`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      credentials: "include",
      body: JSON.stringify({ username, password }),
    });

    if (!res.ok) {
      const errorData = await res.json().catch(() => ({}));
      throw new Error(errorData.message || "Usuario o contraseña incorrectos");
    }
    return; // ya no hay accessToken/refreshToken en body
  } catch (err) {
    console.error(`[Auth Login Error] ${API_URL}/auth/login:`, err);
    throw err;
  }
}

// ---------------- REGISTER ----------------
export async function registerRequest({ fullName, username, email, password, confirmPassword }) {
  const res = await fetch(`${API_URL}/auth/register`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    credentials: "include",
    body: JSON.stringify({ fullName, username, email, password, confirmPassword }),
  });

  if (!res.ok) {
    const errorData = await res.json();
    throw errorData;
  }
  return; // tokens ya están en cookies
}

// ---------------- LOGOUT ----------------
export async function logoutRequest() {
  const res = await fetch(`${API_URL}/auth/logout`, {
    method: "POST",
    credentials: "include", // enviar cookies para que se borren
  });

  if (!res.ok) throw new Error("Error al cerrar sesión");
  return;
}

// ---------------- VERIFY SESSION ----------------
export async function verifySessionRequest() {
  try {
    const res = await fetch(`${API_URL}/auth/verify`, {
      method: "GET",
      credentials: "include",
    });

    if (res.ok) return true;

    if (res.status === 401) {
      const refreshed = await refreshTokenRequest();
      if (!refreshed) return false;

      const retryRes = await fetch(`${API_URL}/auth/verify`, {
        method: "GET",
        credentials: "include",
      });

      return retryRes.ok;
    }

    return false;
  } catch (err) {
    return false;
  }
}


// ---------------- REFRESH TOKEN ----------------
export async function refreshTokenRequest() {
  try {
    const res = await fetch(`${API_URL}/auth/refresh`, {
      method: "POST",
      credentials: "include",
    });

    return res.ok;
  } catch {
    return false;
  }
}
