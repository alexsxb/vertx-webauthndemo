#!/usr/bin/env sh
# Erzeugt ein selbstsigniertes Dev-Zertifikat für localhost (10 Jahre gültig).
# Reines openssl, kein mkcert/Systeminstall nötig -> läuft auf jedem Client.
# Browser zeigen beim ersten Aufruf eine Zertifikatswarnung (nicht vertrauenswürdige
# CA) - einmalig akzeptieren, genau wie beim Vite-Dev-Server.
set -eu
cd "$(dirname "$0")"

openssl req -x509 -nodes -newkey rsa:2048 \
  -keyout localhost-key.pem \
  -out localhost.pem \
  -days 3650 \
  -subj "/CN=localhost" \
  -addext "subjectAltName=DNS:localhost,IP:127.0.0.1,IP:::1"

echo "Erzeugt: $(pwd)/localhost.pem + localhost-key.pem"
