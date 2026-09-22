#!/usr/bin/env bash
# Adaptación directa del script del profesor. No ejecuta servicios ni modifica confianza.
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
FORCE=false
if [[ "${1:-}" == "--force" ]]; then FORCE=true; shift; fi
if [[ $# -ne 0 ]]; then echo "Uso: bash scripts/generar-certificados.sh [--force]" >&2; exit 1; fi
SERVICES=(banco-legacy-auth banco-legacy-web-bff banco-legacy-mobile-bff banco-legacy-atm-bff)
for service in "${SERVICES[@]}"; do
  destination="$ROOT_DIR/$service/src/main/resources"
  [[ -d "$destination" ]] || { echo "Directorio de módulo ausente: $service" >&2; exit 1; }
  if [[ -e "$destination/keystore.p12" && "$FORCE" != true ]]; then
    echo "Ya existen keystores. Use --force sólo para rotarlos deliberadamente." >&2
    exit 1
  fi
done
ACCOUNT_KEYSTORE="$ROOT_DIR/.local/tls/account-service-keystore.p12"
if [[ -e "$ACCOUNT_KEYSTORE" && "$FORCE" != true ]]; then
  echo "Ya existe el keystore externo de Account Service. Use --force sólo para rotarlo deliberadamente." >&2
  exit 1
fi
# Contraseña académica oficial; exportar una diferente antes de ejecutar si se desea.
export TLS_KEYSTORE_PASSWORD="${TLS_KEYSTORE_PASSWORD:-changeit}"
[[ ${#TLS_KEYSTORE_PASSWORD} -ge 6 ]] || { echo "Contraseña demasiado corta" >&2; exit 1; }
mkdir -p "$ROOT_DIR/.local/tls"
TMP="$(mktemp "$ROOT_DIR/.local/tls/temporary-XXXXXXXX.p12")"
rm -f -- "$TMP" # keytool necesita un archivo inexistente; ruta concreta creada por mktemp.
trap 'rm -f -- "$TMP"' EXIT
keytool -genkeypair \
  -alias bff-local -keyalg RSA -keysize 2048 -storetype PKCS12 \
  -keystore "$TMP" -validity 365 \
  -storepass:env TLS_KEYSTORE_PASSWORD -keypass:env TLS_KEYSTORE_PASSWORD \
  -dname "CN=localhost, OU=BackendIII, O=DuocUC, L=VinaDelMar, ST=Valparaiso, C=CL" \
  -ext "SAN=dns:localhost,ip:127.0.0.1"
keytool -exportcert -rfc -alias bff-local -keystore "$TMP" \
  -storepass:env TLS_KEYSTORE_PASSWORD -file "$ROOT_DIR/.local/tls/localhost.crt"
for service in "${SERVICES[@]}"; do
  cp -- "$TMP" "$ROOT_DIR/$service/src/main/resources/keystore.p12"
done
cp -- "$TMP" "$ACCOUNT_KEYSTORE"
rm -f -- "$ROOT_DIR/.local/tls/week6-truststore.p12"
keytool -importcert -noprompt -alias banco-local-ca \
  -file "$ROOT_DIR/.local/tls/localhost.crt" \
  -keystore "$ROOT_DIR/.local/tls/week6-truststore.p12" \
  -storetype PKCS12 -storepass changeit
rm -f -- "$ROOT_DIR/banco-legacy-account-service/src/main/resources/keystore.p12"
echo "Certificado de laboratorio copiado a Auth, Web, Mobile y ATM."
echo "Certificado público: .local/tls/localhost.crt. No se modificó confianza del sistema."
echo "Keystore externo de Account Service y truststore cliente de Mobile disponibles en .local/tls/."
echo "Antes de iniciar, ejecute mvn -B clean verify para actualizar los certificados empaquetados de Auth y Mobile."
