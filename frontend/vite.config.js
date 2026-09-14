import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import basicSsl from '@vitejs/plugin-basic-ssl';

// https statt http: Erweiterungen wie KeePassXC-Browser klinken sich in
// navigator.credentials nur auf https:// ein (localhost über http wird vom
// Browser selbst zwar als "secure context" behandelt, von solchen
// Extensions aber nicht erkannt). basicSsl erzeugt dafür automatisch ein
// selbstsigniertes Dev-Zertifikat, ganz ohne mkcert/Systeminstallation.
export default defineConfig({
  plugins: [react(), basicSsl()],
  server: {
    port: 5173,
    https: true,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
});
