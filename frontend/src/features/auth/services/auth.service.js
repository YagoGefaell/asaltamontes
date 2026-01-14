const API_URL = import.meta.env.VITE_API_URL || "http://localhost:8080/auth";

export async function loginRequest({ email, password }) {
  const res = await fetch(`${API_URL}/login`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ email, password }),
  });
  if (!res.ok) throw new Error("Login failed");
  return res.json(); // { accessToken, refreshToken }
}

export async function registerRequest({ name, email, password }) {
  const res = await fetch(`${API_URL}/register`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ name, email, password }),
  });
  if (!res.ok) throw new Error("Register failed");
  return res.json(); // { accessToken, refreshToken }
}
