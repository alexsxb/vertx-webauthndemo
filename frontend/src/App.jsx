import { useEffect, useState } from 'react';
import { startRegistration, startAuthentication } from '@simplewebauthn/browser';
import { api } from './api.js';

const inputClass =
  'mt-1 w-full rounded-lg border border-slate-300 bg-white px-3 py-2 text-slate-900 shadow-sm ' +
  'placeholder:text-slate-400 focus:border-indigo-500 focus:outline-none focus:ring-2 focus:ring-indigo-500/30 ' +
  'dark:border-slate-700 dark:bg-slate-800 dark:text-slate-100 dark:placeholder:text-slate-500';

const primaryButtonClass =
  'w-full rounded-lg bg-indigo-600 px-4 py-2.5 font-medium text-white shadow-sm transition ' +
  'hover:bg-indigo-500 focus:outline-none focus:ring-2 focus:ring-indigo-500/50 ' +
  'disabled:cursor-not-allowed disabled:bg-slate-300 dark:disabled:bg-slate-700';

export default function App() {
  const [email, setEmail] = useState('');
  const [displayName, setDisplayName] = useState('');
  const [loginEmail, setLoginEmail] = useState('');
  const [user, setUser] = useState(null);
  const [status, setStatus] = useState('');
  const [statusType, setStatusType] = useState('info');
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
      // Response-Endpunkt liefert 204 ohne Body (WebAuthn4JHandler) -> Username kennen wir eh schon.
      await api.registerVerify(attResp);
      setStatusType('success');
      setStatus(`Passkey registriert für ${email}. Du kannst dich jetzt einloggen.`);
    } catch (err) {
      setStatusType('error');
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
      // Response-Endpunkt liefert 204 ohne Body -> Username nach dem Login separat holen
      // (bei Discoverable-Login kennen wir ihn vorher gar nicht).
      await api.loginVerify(authResp);
      const me = await api.me();
      setStatusType('success');
      setStatus(`Eingeloggt als ${me.username}.`);
      setUser(me);
    } catch (err) {
      setStatusType('error');
      setStatus(`Login fehlgeschlagen: ${err.message}`);
    } finally {
      setBusy(false);
    }
  }

  async function handleLogout() {
    await api.logout();
    setUser(null);
    setStatusType('info');
    setStatus('Abgemeldet.');
  }

  return (
    <div className="min-h-screen bg-gradient-to-br from-slate-100 via-white to-indigo-50 px-4 py-12 dark:from-slate-950 dark:via-slate-900 dark:to-indigo-950">
      <div className="mx-auto w-full max-w-md">
        <div className="mb-8 text-center">
          <div className="mx-auto mb-4 flex h-14 w-14 items-center justify-center rounded-2xl bg-indigo-600 text-2xl shadow-lg shadow-indigo-600/30">
            🔑
          </div>
          <h1 className="text-2xl font-semibold text-slate-900 dark:text-slate-100">Passkey Demo</h1>
          <p className="mt-1 text-sm text-slate-500 dark:text-slate-400">Vert.x + WebAuthn4J + MongoDB</p>
        </div>

        <div className="overflow-hidden rounded-2xl border border-slate-200 bg-white shadow-xl shadow-slate-900/5 dark:border-slate-800 dark:bg-slate-900 dark:shadow-black/20">
          {user ? (
            <div className="p-8 text-center">
              <p className="text-slate-600 dark:text-slate-300">
                Angemeldet als <strong className="text-slate-900 dark:text-slate-100">{user.username}</strong>
              </p>
              <button
                onClick={handleLogout}
                className="mt-6 w-full rounded-lg border border-slate-300 px-4 py-2.5 font-medium text-slate-700 transition hover:bg-slate-50 focus:outline-none focus:ring-2 focus:ring-indigo-500/30 dark:border-slate-700 dark:text-slate-200 dark:hover:bg-slate-800"
              >
                Logout
              </button>
            </div>
          ) : (
            <div className="divide-y divide-slate-100 dark:divide-slate-800">
              <section className="p-6">
                <h2 className="mb-4 text-sm font-semibold uppercase tracking-wide text-slate-500 dark:text-slate-400">
                  Registrieren
                </h2>
                <form onSubmit={handleRegister} className="space-y-4">
                  <label className="block text-sm font-medium text-slate-700 dark:text-slate-300">
                    E-Mail
                    <input
                      type="email"
                      required
                      value={email}
                      onChange={(e) => setEmail(e.target.value)}
                      className={inputClass}
                      placeholder="du@example.com"
                    />
                  </label>
                  <label className="block text-sm font-medium text-slate-700 dark:text-slate-300">
                    Anzeigename <span className="font-normal text-slate-400 dark:text-slate-500">(optional)</span>
                    <input
                      type="text"
                      value={displayName}
                      onChange={(e) => setDisplayName(e.target.value)}
                      className={inputClass}
                      placeholder="Max Mustermann"
                    />
                  </label>
                  <button type="submit" disabled={busy} className={primaryButtonClass}>
                    {busy ? 'Wird ausgeführt …' : 'Passkey registrieren'}
                  </button>
                </form>
              </section>

              <section className="p-6">
                <h2 className="mb-4 text-sm font-semibold uppercase tracking-wide text-slate-500 dark:text-slate-400">
                  Login
                </h2>
                <form onSubmit={handleLogin} className="space-y-4">
                  <label className="block text-sm font-medium text-slate-700 dark:text-slate-300">
                    E-Mail <span className="font-normal text-slate-400 dark:text-slate-500">(leer = Discoverable-Login ohne Username)</span>
                    <input
                      type="email"
                      value={loginEmail}
                      onChange={(e) => setLoginEmail(e.target.value)}
                      className={inputClass}
                      placeholder="du@example.com"
                    />
                  </label>
                  <button type="submit" disabled={busy} className={primaryButtonClass}>
                    {busy ? 'Wird ausgeführt …' : 'Mit Passkey einloggen'}
                  </button>
                </form>
              </section>
            </div>
          )}
        </div>

        {status && (
          <p
            className={
              'mt-4 rounded-lg border px-4 py-3 text-sm ' +
              (statusType === 'error'
                ? 'border-red-200 bg-red-50 text-red-700 dark:border-red-900/50 dark:bg-red-950/40 dark:text-red-300'
                : statusType === 'success'
                  ? 'border-emerald-200 bg-emerald-50 text-emerald-700 dark:border-emerald-900/50 dark:bg-emerald-950/40 dark:text-emerald-300'
                  : 'border-slate-200 bg-slate-50 text-slate-600 dark:border-slate-700 dark:bg-slate-800 dark:text-slate-300')
            }
          >
            {status}
          </p>
        )}
      </div>
    </div>
  );
}
