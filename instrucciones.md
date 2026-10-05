# Instrucciones de ejecución y evaluación EFT

Guía del estado `eft` al 5 de octubre de 2026. Comandos reutilizados de Etapas 4–6 o de Semana 8; no se ejecutaron nuevamente los escenarios funcionales durante la auditoría documental. Los endpoints modernos se prueban desde la red Compose: Customer y Payment no publican puertos al host. Ejecutar desde la raíz del repositorio en PowerShell 7.

## 1. Requisitos previos

Java/JDK 21 (`java` y `keytool` en PATH), Maven 3.x, Docker Desktop con contenedores Linux o Docker Engine, Compose compatible con `!reset`, PowerShell 7 y Python 3. El entorno observado usa Java 21.0.12, Maven 3.9.16, Python 3.14.0 y Compose 5.5.1. El override se validó con esa versión; comprobar compatibilidad antes de usar otra.

Los tests Batch requieren los CSV académicos en `../bank_legacy_data/data/semana_1`, `semana_2` y `semana_3`. Compose monta Semana 3 mediante `BATCH_DATA_DIR`; obtener el conjunto académico, no sustituirlo por datos inventados.

## 2. Preparación del entorno

```powershell
pwsh ./scripts/initialize-compose-environment.ps1
docker compose -f docker-compose.yaml config --quiet
```

El inicializador idempotente crea/reutiliza `.env` y `.local/compose/`: credenciales PostgreSQL/OAuth, firma JWT y keystore/truststore TLS. Revisar `.env.example` como esquema de variables, nunca copiar secretos a documentación. No usar `-RotateSecrets` para ejecutar la evaluación sobre datos existentes: una rotación exige sincronizar las credenciales con la BD y los servicios.

Certificado local vigente incluye localhost y nombres de servicios, incluidos Customer/Payment. Usar `.local/compose/banco-legacy-docker.crt` como confianza local; no usar opciones que desactiven verificación TLS. No versionar `.env`, `.local`, JWT o claves privadas.

## 3. Build y tests

```powershell
mvn verify
```

Para habilitar las seis pruebas de claims contra PostgreSQL Compose en host 5433, sin revelar contraseña:

```powershell
./scripts/test-eft-scale.ps1 -TestMode Focused -LogName evaluacion-focused.log
./scripts/test-eft-scale.ps1 -TestMode Verify -LogName evaluacion-verify.log
```

El helper ejecuta Maven, carga credenciales en memoria y restaura las variables del proceso. Los tests reales crean/eliminan exclusivamente schemas temporales `eft6_test_UUID`. Sin `EFT_POSTGRES_TEST_URL`, `mvn verify` omite esas seis pruebas. Cuatro KafkaReal heredados retornan sin flag externo; el total Surefire no implica que hayan probado el broker Docker.

## 4. Levantar entorno base

```powershell
docker compose -f docker-compose.yaml build
docker compose -f docker-compose.yaml up -d
docker compose -f docker-compose.yaml ps
```

Base: 13 servicios, Customer/Account/Payment 1+1+1; tres BFF singleton. No añadir los overrides históricos `compose.eft.yaml` o `compose.scale-local.yaml` a esta secuencia.

Batch estable usa `BATCH_RUN_ON_STARTUP=false`. Los BFF necesitan resultados legacy cargados: la cuenta 101 estaba disponible en la instancia validada, pero no existe por defecto en una BD vacía. Para una BD nueva, seguir **una sola vez** la carga Batch controlada de [Semana 8](docs/semana-8-docker.md#construcción-y-arranque). Esa carga tiene evidencia histórica; no repetirla sobre el volumen evaluado ni eliminar el contenedor importador existente.

## 5. Health checks

```powershell
docker compose ps
curl.exe --cacert .local/compose/banco-legacy-docker.crt https://localhost:8081/actuator/health
curl.exe --cacert .local/compose/banco-legacy-docker.crt https://localhost:8082/actuator/health
curl.exe --cacert .local/compose/banco-legacy-docker.crt https://localhost:8083/actuator/health
curl.exe --cacert .local/compose/banco-legacy-docker.crt https://localhost:8085/actuator/health
```

URLs y verificación CA/hostname usadas por los runners. No confundir un health de disponibilidad con prueba de confianza TLS: algunos healthchecks heredados del Compose base no verifican confianza; las requests del runner sí. Eureka: http://localhost:8761; Config: http://localhost:8888. PostgreSQL: localhost:5433; Kafka: kafka:9092 interno.

## 6. OAuth sin exponer secretos

Auth: `https://localhost:8084/oauth2/token`; grant `client_credentials`; autenticación HTTP Basic con secreto obtenido de `.env` en memoria. No escribir el secreto como argumento CLI ni imprimir la respuesta del token.

| Cliente | Variable del secreto | Scopes |
|---|---|---|
| banco-web-bff | OAUTH_WEB_CLIENT_SECRET | accounts.web |
| banco-mobile-bff | OAUTH_MOBILE_CLIENT_SECRET | accounts.mobile |
| banco-atm-bff | OAUTH_ATM_CLIENT_SECRET | accounts.atm |
| banco-domain-operator | OAUTH_DOMAIN_CLIENT_SECRET | accounts.read accounts.write customers.read customers.write |
| banco-payment-operator | OAUTH_PAYMENT_CLIENT_SECRET | payments.read payments.write accounts.post accounts.post.read |

La función `token` de [validate-eft-compose.py](scripts/validate-eft-compose.py) realiza la emisión con CA/hostname verificados y redacta JWT/secretos en evidencia. `tokens` obtiene los clientes registral y financiero; el runner BFF usa los tres clientes de canal. Token válido permite acceso; ausente produce 401; otro rol de canal o scopes modernos insuficientes produce 403. Los BFF autorizan por ROLE, no por un control SCOPE adicional. Es autenticación técnica, no login de titulares.

## 7. Probar BFF

| Canal | Request histórica validada | Resultado |
|---|---|---|
| Web | GET https://localhost:8081/api/web/accounts/101/dashboard | 200, dashboard detallado |
| Mobile | GET https://localhost:8082/api/mobile/accounts/101/summary | 200, resumen reducido |
| ATM | GET https://localhost:8083/api/atm/accounts/101/balance | 200, saldo esencial |

Usar token del canal correspondiente en `Authorization: Bearer` conservado en memoria. La función `request` de [validate-bff-tls.py](scripts/validate-bff-tls.py) permite repetir esas lecturas con TLS verificado; no volcar tokens en terminal. Sin token 401 y otro canal 403.

**No ejecutar automáticamente la fase regression histórica:** compara IDs de infraestructura contra la captura previa Etapa 5, que ya no coincide con el ciclo de recreaciones Etapa 6; además sobrescribe evidencias. Esa es una precondición del runner, no un fallo de los contratos. En el estado local Etapa 6, la fase `final` del runner scale verifica la regresión vigente, si se conserva su state/snapshot. Consultar las evidencias existentes antes de repetir.

## 8–10. Customer, Account y Payment

La fase `baseline` del runner Etapa 4 ejecutó los contratos siguientes con UUID, IDs y keys nuevos, TLS real y tokens registral/financiero. Su función `http` invoca curl dentro de Payment, leyendo cabeceras por stdin y sin exponer secretos.

| Dominio | Método / ruta | Body y validación |
|---|---|---|
| Customer alta | PUT /api/customers/{UUID} | {name}; 201, replay idéntico 200 |
| Customer consulta | GET /api/customers/{UUID} | 200 |
| Customer actualización | PATCH /api/customers/{UUID} | {name,version:0}; 200/version 1 |
| Account apertura | PUT /api/accounts/{id} | {accountType:"ahorro",customerIds:[UUID]}; 201/ACTIVE/saldo 0 |
| Account consulta | GET /api/accounts/{id} | 200, maestro y saldo operacional |
| Account mantenimiento | PATCH /api/accounts/{id} | {accountType:"ahorro",version:0}; 200/version 1 |
| Payment depósito | POST /api/payments/deposits | {accountId,amount:100} + Idempotency-Key; 201 |
| Payment transferencia | POST /api/payments/transfers | {sourceAccountId,targetAccountId,amount:30} + key; 201 |
| Payment pago | POST /api/payments | {sourceAccountId:destino,amount:10} + key; 201 |
| Payment replay | Repetir mismo POST/key/body/actor | 200, comprobante idéntico |
| Payment conflicto | Misma key con importe 101 | 409, ningún posting adicional |
| Account cierre | POST /api/accounts/{id}/closure | {version:versión actual}; 200/CLOSED |
| Pago hacia cerrada | Depósito/transferencia sobre cuenta cerrada | 409; saldo preservado |

Los importes son fixtures académicas demostradas, no reglas monetarias externas. El pago es un débito registrado sin comercio/adquirente. No usar cuentas modernas en endpoints BFF legacy. Cierre conserva saldo y no liquida fondos.

**Secuencia para una copia de evaluación nueva**, sin `.local/etapa4-state.json` previo y después de health:

```powershell
python -B scripts/validate-eft-compose.py baseline
python -B scripts/validate-eft-compose.py resilience
python -B scripts/validate-eft-compose.py kafka
```

Esta secuencia escribe fixtures, detiene temporalmente Account y envía un JSON inválido controlado al topic financiero. No ejecutarla en una instancia compartida durante uso normal. `baseline` sobrescribe su state y evidencias: **no repetirla en el entorno actual** ni borrar state para aparentar una nueva ejecución. Para revisar sin crear dinero, consultar los JSON registrados y conservar los IDs/keys de sus comprobantes; repetir un POST exacto es idempotente, usar una key nueva no lo es.

## 11. Kafka, outbox y audit

CLI utilizada en Etapa 4:

```powershell
docker compose exec -T kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --describe --topic banco.operaciones.completadas.v1
docker compose exec -T kafka /opt/kafka/bin/kafka-consumer-groups.sh --bootstrap-server localhost:9092 --describe --group financial-payment-audit
```

Esperar tres particiones; lag 0 en las capturas finales. La función `sql` del runner usa psql con `BEGIN READ ONLY` para consultar `eft_financial_outbox`, `eft_account_posting`, `eft_payment_operation` y `eft_payment_event_audit`. Ver [outbox coordinada](docs/evidence/eft/etapa6-08-outbox-coordinada.json) y [detalle persistido](docs/evidence/eft/22-postgresql-detalle.json). No editar estados por SQL.

## 12. Resilience4j

Prueba recomendada para el base y los datos legacy existentes, usada en Etapa 5:

```powershell
python -B scripts/validate-bff-tls.py resilience
```

Comprueba Mobile 200, detiene **solo Account**, espera 503 controlado, restaura Account en finally y comprueba recuperación 200. Genera/sobrescribe evidencias Etapa 5 y requiere los mismos contenedores durante la prueba; revisar/archivar las capturas antes de reproducir. No se ejecutó nuevamente en Etapa 7.

## 13. Escalabilidad horizontal

Usar exclusivamente [despliegue.md](despliegue.md). Contiene pruebas previas, override 2+2+2, routing/failover y retorno. No escalar Batch, Auth, BFF ni infraestructura. El state scale existente bloquea la creación repetida de fixtures.

## 14. Restaurar estado base

Comando probado tras Etapa 6:

```powershell
docker compose -f docker-compose.yaml up -d --no-deps --wait --wait-timeout 240 --scale customer-service=1 --scale account-service=1 --scale payment-service=1 customer-service account-service payment-service web-bff mobile-bff atm-bff
docker compose ps
```

Esto restaura réplicas/configuración, **no borra fixtures**. Conservar `banco-legacy_postgres-data`. Prohibido `down -v`, resetear tablas o eliminar volúmenes para producir una prueba limpia.

## 15. Troubleshooting mínimo

| Síntoma | Comprobación / corrección |
|---|---|
| Docker pipe/daemon inaccesible | Habilitar Docker Desktop Linux Engine y repetir lectura de estado |
| TLS no confiable | Comparar certificado servido con .local/compose; comprobar SAN/validez y CA usada, no desactivar TLS |
| Imagen vieja/SQL ausente | Reconstruir/recrear exclusivamente servicios afectados; diagnóstico Etapas 4/5 |
| Puerto 8085 ocupado al escalar | Aplicar override scale, que elimina binding host Account; no cambiar el base |
| .env ausente/incompleto | Ejecutar inicializador sin rotación y comprobar nombres de variables sin mostrar valores |
| Tests Batch no encuentran CSV | Revisar árbol académico sibling y BATCH_DATA_DIR; no confundir ruta de tests con montaje Docker |
| BD nueva sin legacy | Carga Batch única controlada; no usar runner BFF antes de tener una cuenta histórica |
| Circuito todavía abierto tras restaurar | Esperar health y ventana de recuperación; repetir la misma key/request, no crear operaciones nuevas |
| Runner no satisface precondiciones | Consultar su state/captura y evidencia histórica; no eliminar artefactos para saltar guards |

Para alcance, límites y entregas pendientes, consultar [auditoría final](docs/eft/auditoria-documental-final.md).
