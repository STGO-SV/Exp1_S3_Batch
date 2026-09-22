# Semana 6 — tres BFF y lecturas compartidas

Web, Mobile y ATM consumen Config Server, se registran en Eureka y resuelven
`banco-legacy-account-service` con Spring Cloud LoadBalancer. Cada BFF valida localmente el JWT RS256 y reenvía
el mismo Bearer a Account Service, que valida firma, issuer, audience, vigencia y rol de nuevo. Las comunicaciones
externas y BFF → Account Service son HTTPS con el truststore de laboratorio; Config Server y Eureka permanecen en
HTTP loopback. No hay Gateway.

| Canal | GET externo | GET interno | Rol |
|---|---|---|---|
| Web | `/api/web/accounts/{id}/dashboard` | `/internal/accounts/{id}/web-dashboard` | WEB |
| Mobile | `/api/mobile/accounts/{id}/summary`, `/movements` | `/internal/accounts/{id}/summary`, `/movements` | MOBILE |
| ATM | `/api/atm/accounts/{id}/balance`, `/movements` | `/internal/accounts/{id}/atm-balance`, `/atm-movements` | ATM |

Account Service usa las consultas optimizadas de Core; sus límites de movimientos/anomalías están en
`config-repository/banco-legacy-account-service.yml`. El dashboard Web conserva sus campos, incluidos movimientos
y anomalías globales. ATM conserva las respuestas reducidas de saldo y movimientos. Las lecturas del downstream
son exclusivamente GET. La separación de Core como servicio independiente y el rediseño del dominio quedan como
evolución posterior.

El POST `/api/atm/accounts/{id}/withdrawals` continúa local en ATM BFF, con `@Transactional`, bloqueo
`SELECT ... FOR UPDATE`, actualización por id e inserción de movimiento. No tiene Circuit Breaker ni Retry.
Account Service lee la misma PostgreSQL y por ello los GET posteriores a un retiro confirmado deben observar el
saldo actualizado. No ejecutar Batch junto con ATM: Batch puede volver a procesar el dataset.

Los tres BFF tienen Circuit Breaker `accountService` independientes, timeout de conexión de 2 s y de lectura
de 3 s. La configuración común vive en Config Server. Un fallo de lectura devuelve HTTP 503 con
`ACCOUNT_SERVICE_UNAVAILABLE` y sin datos financieros fabricados. Un 404 de cuenta se conserva como 404.

## Arranque y comprobación

1. Preparar las variables externas de JWT, contraseñas demo y PostgreSQL; no escribirlas en archivos versionados.
2. Si faltan certificados de laboratorio, ejecutar `./scripts/generar-certificados.ps1 -Force`.
3. Con los JAR cerrados, ejecutar `mvn -B clean verify`.
4. Ejecutar `./scripts/start-week6.ps1` desde la misma sesión. Inicia Config Server, Eureka, Auth,
   Account Service y los tres BFF. No inicia Batch.
5. Ejecutar `./scripts/verify-week6.ps1 -AccountId 101`: comprueba Config, los cuatro registros UP,
   Web/Mobile/ATM GET 200, sus circuitos CLOSED y ausencia de URL física de Account Service en clientes Java.
6. Sólo en un entorno de laboratorio preparado, ejecutar `./scripts/verify-week6-resilience.ps1 -AccountId 101`.
   Esta prueba detiene el único proceso Java verificado en 8085, observa 503/OPEN/HALF_OPEN de los tres BFF,
   reinicia Account Service y comprueba 200/CLOSED. No toca ATM POST. Guarda observaciones saneadas bajo
   `docs/evidence/semana-6/live/<timestamp>/`, sin tokens ni saldos.

La ejecución live del piloto Mobile anterior pasó y quedó en `docs/evidence/semana-6/live/20260919-182710`.
Esa carpeta no demuestra todavía el estado live de Web ni ATM. La colección
`docs/postman/semana-6.postman_collection.json` contiene solicitudes para las consultas y la matriz JWT.
El verificador de resiliencia sólo debe ejecutarse cuando las variables y servicios requeridos estén presentes;
no se ejecuta como parte del build.
