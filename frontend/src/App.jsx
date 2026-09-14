import { useEffect, useState } from 'react';
import { startRegistration, startAuthentication } from '@simplewebauthn/browser';
import { api } from './api.js';

export default function App() {
  const [email, setEmail] = useState('');
  const [displayName, setDisplayName] = useState('');
  const [loginEmail, setLoginEmail] = useState('');
  const [user, setUser] = useState(null);
  const [status, setStatus] = useState('');
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    api.me().then(setUser).catch(() => setUser(null));
  }, []);

  async function handleRegister(e) {
    e.preventDefault();
    setBusy(true);
    setStatus('');
    try {
      const options = await api.registerOptions(email, displayName || email);
      const attResp = await startRegistration({ optionsJSON: options });
      const result = await api.registerVerify(attResp);
      setStatus(`Passkey registriert für ${result.username}. Du kannst dich jetzt einloggen.`);
    } catch (err) {
      setStatus(`Fehler bei der Registrierung: ${err.message}`);
    } finally {
      setBusy(false);
    }
  }

  // Discoverable-Login: leeres email-Feld -> Browser fragt Authenticator direkt
  // nach passenden Credentials für diese Domain (Username-less Flow).
  async function handleLogin(e) {
    e.preventDefault();
    setBusy(true);
    setStatus('');
    try {
      const options = await api.loginOptions(loginEmail || undefined);
      const authResp = await startAuthentication({ optionsJSON: options });
      const result = await api.loginVerify(authResp);
      setStatus(`Eingeloggt als ${result.username}.`);
      setUser({ username: result.username });
    } catch (err) {
      setStatus(`Login fehlgeschlagen: ${err.message}`);
    } finally {
      setBusy(false);
    }
  }

  async function handleLogout() {
    await api.logout();
    setUser(null);
    setStatus('Abgemeldet.');
  }

  return (
    <div style={{ maxWidth: 480, margin: '40px auto', fontFamily: 'sans-serif' }}>
      <h1>Passkey Demo (Vert.x + WebAuthn4J + MongoDB)</h1>

      {user ? (
        <div>
          <p>Angemeldet als <strong>{user.username}</strong></p>
          <button onClick={handleLogout}>Logout</button>
        </div>
      ) : (
        <>
          <section style={{ marginBottom: 32 }}>
            <h2>Registrieren</h2>
            <form onSubmit={handleRegister}>
              <div>
                <label>E-Mail<br />
                  <input
                    type="email"
                    required
                    value={email}
                    onChange={(e) => setEmail(e.target.value)}
                  />
                </label>
              </div>
              <div>
                <label>Anzeigename (optional)<br />
                  <input
                    type="text"
                    value={displayName}
                    onChange={(e) => setDisplayName(e.target.value)}
                  />
                </label>
              </div>
              <button type="submit" disabled={busy}>Passkey registrieren</button>
            </form>
          </section>

          <section>
            <h2>Login</h2>
            <form onSubmit={handleLogin}>
              <div>
                <label>E-Mail (leer lassen für Discoverable-Login ohne Username)<br />
                  <input
                    type="email"
                    value={loginEmail}
                    onChange={(e) => setLoginEmail(e.target.value)}
                  />
                </label>
              </div>
              <button type="submit" disabled={busy}>Mit Passkey einloggen</button>
            </form>
          </section>
        </>
      )}

      {status && <p style={{ marginTop: 24 }}>{status}</p>}
    </div>
  );
}
