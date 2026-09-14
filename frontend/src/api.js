const BASE = '/api';

async function request(path, body) {
  const res = await fetch(BASE + path, {
    method: 'POST',
    credentials: 'include', // Session-Cookie mitschicken
    headers: { 'Content-Type': 'application/json' },
    body: body === undefined ? undefined : JSON.stringify(body),
  });

  const contentType = res.headers.get('content-type') || '';
  const data = contentType.includes('application/json') ? await res.json() : null;

  if (!res.ok) {
    throw new Error(data?.error || `HTTP ${res.status}`);
  }
  return data;
}

// WebAuthn4JHandler (Vert.x) erwartet "name" statt "email" und verarbeitet
// Attestation (Register) wie Assertion (Login) über denselben Response-Endpunkt.
export const api = {
  registerOptions: (email, displayName) =>
    request('/webauthn/register/options', { name: email, displayName }),
  registerVerify: (attestationResponse) =>
    request('/webauthn/response', attestationResponse),

  loginOptions: (email) => request('/webauthn/login/options', { name: email }),
  loginVerify: (assertionResponse) =>
    request('/webauthn/response', assertionResponse),

  me: async () => {
    const res = await fetch(BASE + '/me', { credentials: 'include' });
    if (!res.ok) return null;
    return res.json();
  },

  logout: () => fetch(BASE + '/logout', { method: 'POST', credentials: 'include' }),
};
