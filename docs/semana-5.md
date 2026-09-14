# Semana 5 — JWT y HTTPS local directo

La solución ejecutable de Semana 5 está formada por cuatro aplicaciones Spring Boot: Auth y los BFF Web, Mobile y ATM. Cada proceso sirve HTTPS directamente con un keystore PKCS12 local. Banco Legacy Batch no participa en esta topología y Core es una biblioteca sin servidor web.

| Aplicación | Puerto | URL local |
|---|---:|---|
| Auth | 8084 | https://localhost:8084 |
| Web BFF | 8081 | https://localhost:8081 |
| Mobile BFF | 8082 | https://localhost:8082 |
| ATM BFF | 8083 | https://localhost:8083 |

Auth conserva el puerto 8084 porque ya estaba configurado y no colisiona con los BFF. Recibe credenciales y entrega JWT RS256, por lo que también usa HTTPS. Los clientes envían después el Bearer JWT al BFF correspondiente.

## Certificado de laboratorio

Los scripts scripts/generar-certificados.ps1 y scripts/generar-certificados.sh adaptan el procedimiento del profesor a los cuatro módulos reales. Generan una sola identidad RSA de 2048 bits, PKCS12, alias bff-local, CN localhost, SAN DNS localhost y SAN IP 127.0.0.1, válida durante 365 días. El archivo temporal se elimina después de copiarlo a los recursos de Auth y los tres BFF.

PowerShell:

    ./scripts/generar-certificados.ps1

Bash:

    ./scripts/generar-certificados.sh

Los scripts no reemplazan keystores existentes salvo que se indique Force o --force. La variable TLS_KEYSTORE_PASSWORD permite cambiar la contraseña; su valor predeterminado changeit conserva la reproducibilidad del laboratorio. Changeit no es una estrategia productiva de secretos. Los keystores contienen una clave privada, están ignorados por Git y no deben distribuirse. Para producción se requiere una identidad emitida y custodiada por la organización.

Cada aplicación usa server.ssl.enabled, classpath:keystore.p12, PKCS12 y el alias bff-local. Los contextos de tests desactivan SSL de forma acotada para que los servidores aleatorios de Spring sigan siendo pruebas internas.

## Variables de ejecución

Los tres BFF conservan DB_URL, DB_USER y DB_PASSWORD. Auth conserva sus usuarios demo, hashes BCrypt, claves RSA, issuer, audience y demás variables externas documentadas en el README. Los cuatro procesos deben recibir TLS_KEYSTORE_PASSWORD cuando el keystore no use el valor de laboratorio predeterminado.

## Verificación

El flujo esperado es:

1. Generar los keystores.
2. Compilar con el wrapper Maven.
3. Iniciar únicamente Auth, Web, Mobile y ATM.
4. Obtener el token desde Auth por HTTPS.
5. Enviar el token al canal correcto por HTTPS.

La matriz de autorización debe producir 200 en el canal propio y 403 en los otros dos. La ausencia de token y un token alterado producen 401. Un error interno autorizado conserva 500 o 503. El retiro ATM sigue siendo SIMULATED y no persiste movimientos.

Para una comprobación rápida con certificado autofirmado, Postman puede desactivar SSL certificate verification y curl.exe puede usar -k. Estos modos prueban que TLS está activo, pero no validan confianza ni identidad. La comprobación estricta debe importar o confiar explícitamente en .local/tls/localhost.crt y mantener la validación de nombre. Los scripts de verificación admiten TrustedCertificate para ese propósito; LabSkipCertificateValidation queda marcado como modo de laboratorio.

La colección y el entorno de docs/postman usan https://localhost en los cuatro puertos. Nginx Proxy Manager se conserva en infra como alternativa experimental y no se despliega ni interviene en esta solución.

## Evidencia y límites

La evidencia reproducible está en docs/evidence/semana-5. La validación HTTPS aislada usa H2 sintético porque este proceso no recibió DB_URL, DB_USER ni DB_PASSWORD. No representa PostgreSQL ni modifica datasets. No se ejecutó la aplicación Batch. La validación contra PostgreSQL, EXPLAIN ANALYZE y los resultados de rendimiento con el dataset real siguen pendientes.

La validación final está automatizada para ejecutarse en la sesión PowerShell 7 que ya contiene las variables:

    ./scripts/verify-week5-postgres.ps1

No es necesario iniciar previamente los servicios. El script detecta Auth y los BFF, inicia sólo los ausentes,
renueva tokens por bloque, usa el driver JDBC exclusivamente dentro de transacciones READ ONLY y deja los servicios que
ya estaban activos sin intervenir. Genera verification-https-postgres.json, bff-benchmark-https-postgres.csv,
explain-analyze-postgres.txt, final-validation-summary.txt y un resumen saneado del build.

El TTL permanece en 300 segundos por defecto y conserva el rango 30–900. La expiración se cubre con el test
automatizado existente, evitando esperas largas. La evidencia PostgreSQL no se considera producida hasta que
el script termine con OVERALL: PASS.
