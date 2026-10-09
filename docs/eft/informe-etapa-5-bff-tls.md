# Etapa 5: TLS vigente y regresión de BFF legacy

Fecha: 2026-10-04. Rama: `eft`. Base: `fbcc2d2`.

## Resultado y alcance

Web, Mobile y ATM sirven el certificado TLS vigente y conservan sus contratos históricos. Se validaron HTTPS real, OAuth por canal, 401/403, consultas legacy y fallback de Mobile con Account detenido y restaurado. Tests focalizados y `mvn verify`: BUILD SUCCESS.

No se modificaron fuentes Java, contratos BFF, autorización, lógica de retiro, titularidad ni integración Customer/Payment. No se generaron certificados, rotaron secretos, modificaron datos bancarios, eliminaron volúmenes, hicieron push/merge, desplegaron servicios cloud ni escalaron réplicas.

## Diagnóstico y causa

Antes de intervenir, los tres BFF estaban running/healthy y usaban imágenes `banco-legacy/{web,mobile,atm}-bff:semana-8`, creadas el 2 de octubre. El inicializador actualizó el material TLS durante Etapa 4 para incorporar Customer y Payment a los SAN, pero las JVM BFF seguían sirviendo el certificado cargado anteriormente.

El montaje de `.local/compose` es de solo lectura en `/run/banco-legacy`. Cambiar sus archivos no recarga automáticamente el contexto TLS ni el truststore de una JVM ya iniciada. Los tres certificados servidos eran idénticos entre sí y diferentes al material actual; fallaba la confianza con el certificado vigente.

El diagnóstico inicial no alteró servicios. Para identificar el certificado anterior se verificó el handshake y hostname contra su copia pública archivada en `.local/etapa4-cert-anterior.pem`; también se registró el rechazo al verificar contra el material vigente. Ninguna conexión desactivó la validación TLS.

## Reconstrucción y certificado antes/después

Se reconstruyeron y recrearon únicamente:

- `banco-legacy-web-bff-1`
- `banco-legacy-mobile-bff-1`
- `banco-legacy-atm-bff-1`

Se mantuvieron las etiquetas históricas; la evidencia registra IDs de imagen y contenedor antes/después. Se usaron el keystore existente `banco-legacy-docker.p12` y el truststore `banco-legacy-truststore.p12`. La huella del certificado local antes y después coincide: no se regeneró.

| Propiedad | Antes | Después, los tres BFF |
|---|---|---|
| SHA-256 | `69e205ef60524eb442d117b536a21abf65637b69949b0f4cb209774e6e5a1502` | `0fa02588c1bfe85712639d2a98164ab6ebb3b0c0a77358b0af611d394090fd9c` |
| Serie | `40ED95BFC589AAA3` | `E0C3377D7BF063ED` |
| Inicio UTC | Oct  2 18:37:30 2026 GMT | Oct  4 21:04:37 2026 GMT |
| Fin UTC | Oct  2 18:37:30 2027 GMT | Oct  4 21:04:37 2027 GMT |
| Confianza con material vigente | Rechazada | Verificada |
| TLS | TLSv1.3 | TLSv1.3 |

Issuer y subject: `CN=Banco Legacy Local, OU=Semana 8, O=DUOC, L=Santiago, C=CL`, certificado local autofirmado utilizado como ancla de confianza.

SAN anterior: DNS `localhost, auth-server, account-service, web-bff, mobile-bff, atm-bff`; IP `127.0.0.1`.

SAN vigente: los anteriores más DNS `customer-service` y `payment-service`.

Se verificaron los tres puertos 8081/8082/8083 con `ssl.create_default_context(cafile=certificado_vigente)` y `server_hostname=localhost`: confianza, validez temporal, hostname y coincidencia SHA-256. `/actuator/health` respondió 200 en los tres BFF. No se utilizó `-k`, `--insecure` ni un contexto sin verificación.

Todos los IDs ajenos a BFF se conservaron durante la recreación. Account se detuvo y arrancó posteriormente solo para resiliencia y conservó su ID. El estado final confirma Account y BFF saludables.

## Contratos Web, Mobile y ATM

Cuenta legacy seleccionada mediante SQL READ ONLY: `101`. PostgreSQL confirma registros en `interes_procesado` y cero registros para esa cuenta en `eft_account`. No se forzó integración moderna.

| Canal | GET verificado | Resultado |
|---|---|---|
| Web | `https://localhost:8081/api/web/accounts/101/dashboard` | 200; contrato completo, saldo procesado 8160, 20 movimientos y 10 anomalías |
| Mobile | `https://localhost:8082/api/mobile/accounts/101/summary` | 200; accountId 101, balance 8160, accountType prestamo |
| ATM | `https://localhost:8083/api/atm/accounts/101/balance` | 200; accountId 101, availableBalance 8160 |

Se comprobaron los campos exactos y consistencia del saldo entre canales. Las respuestas completas están en las evidencias. El dashboard conserva datos y movimientos históricos sin reinterpretarlos como operaciones EFT modernas.

## OAuth y controles de acceso

Tokens reales obtenidos de `https://localhost:8084/oauth2/token` por `client_credentials`, con TLS verificado:

| Cliente | Scope solicitado/otorgado | Rol histórico |
|---|---|---|
| banco-web-bff | accounts.web | WEB |
| banco-mobile-bff | accounts.mobile | MOBILE |
| banco-atm-bff | accounts.atm | ATM |

En cada endpoint: token del canal → 200; sin token → 401; token de otro canal → 403. Se preservó la autorización histórica por rol; los BFF no exigen un scope adicional de forma independiente. La prueba negativa corresponde a rol/canal incorrecto, sin afirmar una política de scopes nueva.

Los secretos se leen desde `.env` en memoria; tokens y credenciales se redactan antes de guardar evidencia.

## Resilience4j de Mobile

Se mantuvo URL `/api/mobile/accounts/101/summary`, cuenta legacy y token:

1. Account disponible: 200, balance 8160.
2. `docker compose stop account-service`: misma consulta → 503, cuerpo controlado `code=ACCOUNT_SERVICE_UNAVAILABLE`.
3. `docker compose start account-service`: tras esperar disponibilidad, misma consulta → 200, balance 8160.

Actuator registró `accountService` con cero fallos antes y uno después. Un fallo no necesariamente abre el circuito: en esta muestra permaneció CLOSED con tasa 33,33%, inferior al umbral 50%. Se probó el fallback; no se afirma una transición OPEN que no ocurrió.

La restauración se ejecuta mediante `finally`. No se recrearon BFF ni infraestructura para recuperar Account; todos quedaron saludables.

## Tests

- `mvn -pl banco-legacy-web-bff,banco-legacy-mobile-bff,banco-legacy-atm-bff -am test`: BUILD SUCCESS; Web 22, Mobile 27, ATM 51 y Core 15, total 115; cero fallos, errores o skipped.
- `mvn verify`: BUILD SUCCESS; 245 pruebas reportadas, cero fallos, errores o skipped; finalización 2026-10-04 18:58:21 -03:00.
- Las validaciones Compose/HTTPS/OAuth/fallback son ejecuciones reales separadas de Maven. Las cuatro pruebas KafkaReal heredadas, condicionadas por flag, no constituyen por sí solas prueba contra el broker Docker; esa evidencia corresponde a Etapa 4.

## Reproducción

Desde la raíz del repositorio, con Compose y `.env` existentes:

```text
python -B scripts/validate-bff-tls.py diagnose
docker compose build web-bff mobile-bff atm-bff
docker compose up -d --no-deps --wait --wait-timeout 180 web-bff mobile-bff atm-bff
python -B scripts/validate-bff-tls.py regression
python -B scripts/validate-bff-tls.py resilience
mvn -pl banco-legacy-web-bff,banco-legacy-mobile-bff,banco-legacy-atm-bff -am test
mvn verify
```

`diagnose` recurre a la copia pública anterior solamente si el material vigente todavía no verifica el certificado servido. `regression` y `resilience` siempre confían exclusivamente en el material vigente. El runner no inicia una nueva PKI ni modifica datos bancarios.

## Evidencias

Todas en `docs/evidence/eft/`:

- [04-oauth-etapa5-atm.json](../evidence/eft/04-oauth-etapa5-atm.json)
- [04-oauth-etapa5-mobile.json](../evidence/eft/04-oauth-etapa5-mobile.json)
- [04-oauth-etapa5-web.json](../evidence/eft/04-oauth-etapa5-web.json)
- [etapa5-01-diagnostico-tls-antes.json](../evidence/eft/etapa5-01-diagnostico-tls-antes.json)
- [etapa5-02-tls-vigente-https-health.json](../evidence/eft/etapa5-02-tls-vigente-https-health.json)
- [etapa5-03-atm-200.json](../evidence/eft/etapa5-03-atm-200.json)
- [etapa5-03-mobile-200.json](../evidence/eft/etapa5-03-mobile-200.json)
- [etapa5-03-web-200.json](../evidence/eft/etapa5-03-web-200.json)
- [etapa5-04-oauth-401-403.json](../evidence/eft/etapa5-04-oauth-401-403.json)
- [etapa5-05-mobile-resilience-503.json](../evidence/eft/etapa5-05-mobile-resilience-503.json)
- [etapa5-06-mobile-recuperacion-200.json](../evidence/eft/etapa5-06-mobile-recuperacion-200.json)
- [etapa5-07-reconstruccion-bff.json](../evidence/eft/etapa5-07-reconstruccion-bff.json)
- [etapa5-08-tests-focalizados.txt](../evidence/eft/etapa5-08-tests-focalizados.txt)
- [etapa5-09-mvn-verify.txt](../evidence/historico/eft/etapa5-09-mvn-verify.txt)
- [etapa5-10-contratos-legacy-consistencia.json](../evidence/eft/etapa5-10-contratos-legacy-consistencia.json)
- [etapa5-11-compose-final-saludable.json](../evidence/eft/etapa5-11-compose-final-saludable.json)

## Git y pendientes

Commit local de runner y evidencias: `2e58b59`. Este informe se registra en un segundo commit local sobre `eft`; su hash se informa al entregar. El informe y el runner son los únicos cambios fuera de evidencias.

Sin pendientes dentro del alcance TLS/OAuth/regresión/resiliencia. La integración BFF con maestros y cuentas modernas, titularidad y contratos correspondientes queda para una etapa posterior autorizada. Escalado horizontal y despliegue cloud no forman parte de esta validación.
