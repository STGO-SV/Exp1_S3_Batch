# Informe Etapa 3 — dominio operacional

Fecha: 4 de octubre de 2026. Rama eft, base be36d83; main permanece en 76b9773.
Trabajo local autorizado; sin push, merge, cloud ni demostración de escalado.

## Decisiones y separación de datos

Customer es maestro de clientes explícitos UUID/name/version, sin PII añadida ni borrado físico.
Account es maestro de cuentas nuevas y vínculos explícitos. customerIds conserva titularidad múltiple de Etapa2; customerId singular cuando hay un titular.
Legacy/Batch no alimenta titulares ni saldo operacional. No se infieren clientes desde los nombres contradictorios de Semana3.
Las cuentas modernas no se habilitan automáticamente en los BFF/ATM legacy; sus endpoints y jobs se conservan.

## Account

Modelo API accountId/accountType/customerIds/customerId/status/balance/version.
ACTIVE al abrir, saldo0; CLOSED conserva registro y saldo y prohíbe nuevas operaciones. Mantenimiento no reabre.
OPEN físico se presenta ACTIVE para conservar constraint y registros existentes sin migración destructiva.
eft_account_balance DECIMAL(19,2) >=0, FK local. Inicialización aditiva rellena0 solo filas registrales de Etapa2 sin saldo; nunca reemplaza un saldo ya existente.
Versión registral reutilizada: compare-and-set mantenimiento/cierre y version+1 en cada posting.
Transferencia bloquea ambas cuentas por ID ascendente. Crédito/débito validan importe positivo, estado y saldo suficiente.
Una transacción local confirma saldos, versiones, comprobante eft_account_posting y eft_financial_outbox.
Rechazo o fallo de outbox revierte todo. La capacidad DECIMAL es restricción de almacenamiento, sin límite de negocio inventado.
Endpoints registrales: PUT/GET/PATCH /api/accounts/{id}, POST /api/accounts/{id}/closure y GET /api/accounts?customerId.
Internos financieros: POST /internal/accounts/postings, GET /internal/accounts/postings/{operationId}, GET /internal/accounts/{id}/operational-balance.

## Customer

Conserva PUT/GET/PATCH /api/customers/{UUID} y GET /api/customers/{UUID}/accounts, paginado.
Alta/consulta/actualización/versiones/validaciones y autorización permanecen probados.
Proyección de cuentas en Customer es registral, sin saldo; consulta directa Account devuelve balance.
Apertura Account valida Customer remoto con JWT original; caída/404 no crea vínculos huérfanos.
Sin importación automática ni eliminación de identidades.

## Payment

Depósito POST /api/payments/deposits {accountId,amount}.
Transferencia POST /api/payments/transfers {sourceAccountId,targetAccountId,amount}.
Pago POST /api/payments {sourceAccountId,amount}: débito registrado sin merchant/adquirente/beneficiario externo.
Consulta GET /api/payments/operations/{operationId}, limitada al actor técnico.
eft_payment_operation guarda operación, actor, clave/hash, request JSON inmutable, PENDING/COMPLETED/FAILED, createdAt, receipt y failureCode/status.
Tipo/cuentas/amount están en request; completedAt y balances posteriores en receipt. Sin JWT ni secretos almacenados.
No transacción abierta durante HTTP. PENDING se confirma antes de llamar a Account.
Rechazos deterministas400/404/409 → FAILED con replay estable; indisponibilidad/timeout/circuito → PENDING y503.
COMPLETED requiere comprobante real validado. Resultado remoto incierto puede haber movido saldo; no se afirma rollback remoto ni ACID distribuida.

## Idempotencia y recuperación

Idempotency-Key obligatorio [A-Za-z0-9._:-], longitud1..120; espacio (sub JWT verificado,clave).
SHA-256 de tipo/cuentas/importe normalizado; 10 equivale a10.00.
Misma clave/payload conserva operationId y resultado; payload distinto409. Restricciones únicas serializan solicitudes concurrentes.
Account reclama clave antes de mover saldo; replay devuelve comprobante original, aun si el saldo vigente cambió.
El registro Payment PENDING se recupera por POST idéntico o evento Kafka correlacionado, sin volver a mover fondos.
Claves sin expiración automática en esta etapa. No se inventa compensación de una transferencia ya atómica localmente.

## Kafka

Topic banco.operaciones.completadas.v1, key operationId, evento v1 {eventId,version,actor,requestHash,result}.
Semánticas DepositCompleted/TransferCompleted/PaymentCompleted derivadas de result.type.
Outbox Account en la misma transacción del saldo. Publisher hasta100 pendientes cada2s; ack5s; intentos/last_error; solo ack exitoso marca PUBLISHED.
Productor acks=all/idempotence, tiempos de espera acotados. Retry conserva eventId; entrega al menos una vez.
Payment consume para auditoría eft_payment_event_audit y reconciliación PENDING, dedup eventId, validación de hash/actor/comprobante.
No modifica saldo; eventos Account directos se auditan sin fabricar operaciones Payment.
Auditoría y proyección se confirman juntas. Eventos inválidos/malformados van a DLT; transitorios tienen2 reintentos.
Fallos al publicar DLT no se marcan recuperados. Flujo de anomalías existente separado e intacto.
Publicador singleton para esta etapa; antes de escalar se debe coordinar selección de outbox y validar despliegue concurrente.

## Resilience4j y seguridad

Payment→Account accountPosting: ventana4, mínimo3, umbral50%, apertura10s, una llamada HALF_OPEN; 4xx no cuentan como caída.
Cliente HTTP connect2s/read3s y timelimiter5s. Fallback503 nunca inventa saldo/comprobante ni persiste COMPLETED.
Account→Customer customerRegistry existente; Customer→Account accountRegistry conservado.
Scopes administrativos accounts.read/write y customers.read/write separados de payments.write/read y accounts.post/post.read.
Operador financiero opcional banco-payment-operator / ROLE_PAYMENT_OPERATOR, secreto OAUTH_PAYMENT_CLIENT_SECRET.
Operador domain no recibe ejecución financiera ni canales; WEB/MOBILE/ATM conservan accounts.web/mobile/atm.
Resource Servers verifican firma RSA, issuer, audience, expiración y claims; health público, resto deny-by-default.
OAuth representa clientes técnicos; no autenticación final ni autorización personal por titularidad inventada.

## Docker y configuración

Payment agregado a Compose base con DB, Kafka, Config, Eureka, JWT, HTTPS/truststore y healthcheck HTTPS8086.
Account configura Kafka; Customer continúa en base. Payment/Customer no publican puerto host.
Account conserva8085 y BFF8081/8082/8083; esos bindings fijos impiden escalar sin override. Se mantienen para compatibilidad.
Postgres5433, Auth8084, Config8888 y Eureka8761 también conservan bindings de infraestructura.
compose.eft.yaml mantiene compatibilidad como override vacío; ya no requiere perfil opcional de Payment.
initialize-compose-environment.ps1 agrega secreto financiero y preserva los anteriores.
Validación aislada desde script be36d83: conserva todos los valores previos, añade solo OAUTH_PAYMENT_CLIENT_SECRET y segunda ejecución no cambia hash .env.
Compose base y base+override pasan config --quiet. Entorno real .env/.local/compose no modificado.
Docker daemon no disponible también después de mvn verify (pipe dockerDesktopLinuxEngine inexistente). No se ejecutó stack, healthchecks reales, PostgreSQL real ni smoke HTTP completo.
Kafka embebido es broker real en tests; no constituye evidencia de Compose ni del broker externo. FinancialOutboxTests simula ack/fallo del productor para comprobar persistencia/retry; FinancialKafkaTests valida entrega, consumo, DLT y proyección con broker embebido.

## Validación

mvn verify: BUILD SUCCESS, 245 tests reportados, 0 fallos, 0 errores, 0 skipped reportados; +38 frente a Etapa2. Todos los proyectos del reactor pasan. Finalizó 17:31:04 -03:00, 4:10 minutos.
Prueba focalizada final de Core/Account/Payment/Auth: BUILD SUCCESS. Evidencia reproducible resumida en validacion-etapa3.txt.
Las pruebas focalizadas incluyen saldos, cierre/versiones, atomicidad/rollback, insuficiencia, concurrencia/idempotencia, fallos de outbox y retry, recuperación HTTP/circuitos/eventos, scopes/JWT y Kafka embebido/DLT.
Mocks de Payment validan orquestación/estado; las pruebas Account usan base H2 real para movimientos y transacción.
PostgreSQL y HTTPS entre contenedores quedan sin ejecutar; no se presentan mocks/H2 como esa evidencia.
Los cuatro tests KafkaReal heredados retornan sin ejercitar broker externo si no se habilita su entorno, aunque Surefire no los contabiliza como skipped.



| Módulo | Tests | Fallos | Errores | Skipped reportados |
|---|---:|---:|---:|---:|
| banco-legacy-account-service | 36 | 0 | 0 | 0 |
| banco-legacy-anomaly-service | 12 | 0 | 0 | 0 |
| banco-legacy-atm-bff | 51 | 0 | 0 | 0 |
| banco-legacy-auth | 11 | 0 | 0 | 0 |
| banco-legacy-batch | 31 | 0 | 0 | 0 |
| banco-legacy-config-server | 2 | 0 | 0 | 0 |
| banco-legacy-core | 15 | 0 | 0 | 0 |
| banco-legacy-customer-service | 16 | 0 | 0 | 0 |
| banco-legacy-discovery-server | 1 | 0 | 0 | 0 |
| banco-legacy-mobile-bff | 27 | 0 | 0 | 0 |
| banco-legacy-payment-service | 21 | 0 | 0 | 0 |
| banco-legacy-web-bff | 22 | 0 | 0 | 0 |

## Git

Commits locales de implementación:
- 3024e47 — Implementa saldo operacional y posting atómico idempotente en Account.
- f5438ca — Implementa Payment persistente con resiliencia y reconciliación Kafka.
- f07b148 — Integra OAuth financiero y Payment funcional en Compose base.

Cierre documental: commit que contiene este informe, titulado Documenta cierre Etapa 3 y evidencia operacional.
Estado final: rama eft, git status --porcelain vacío tras el cierre; main=76b9773.
Sin push, merge, cloud ni demostración de escalado. Consultar git log -4 para el identificador del propio commit documental.

## Decisiones pendientes y siguiente etapa

Ninguna decisión humana bloquea el alcance operacional aprobado.
Fuera de alcance: reserva/migración de IDs ante futuras colisiones Batch, identificación real de personas e IAM/autorización por titularidad.
AWS/entregables finales siguen sujetos a definición docente previa; no se ejecuta cloud aquí.
Siguiente etapa: smoke en Compose/PostgreSQL real, integración de BFF con maestros modernos y preparación de escalado con coordinación de outbox.
La validación real pendiente es trabajo técnico; no exige reinterpretar el saldo inicial ni pago aprobados.

## Archivos modificados

49 archivos respecto de be36d83:

- .env.example
- banco-legacy-account-service/pom.xml
- banco-legacy-account-service/src/main/java/com/duoc/banco_legacy/account/financial/AccountPostingController.java
- banco-legacy-account-service/src/main/java/com/duoc/banco_legacy/account/financial/AccountPostingService.java
- banco-legacy-account-service/src/main/java/com/duoc/banco_legacy/account/financial/FinancialKafkaConfig.java
- banco-legacy-account-service/src/main/java/com/duoc/banco_legacy/account/financial/FinancialOutboxPublisher.java
- banco-legacy-account-service/src/main/java/com/duoc/banco_legacy/account/registry/AccountRegistryService.java
- banco-legacy-account-service/src/main/java/com/duoc/banco_legacy/account/registry/RegistryErrorHandler.java
- banco-legacy-account-service/src/main/java/com/duoc/banco_legacy/account/security/AccountSecurityConfig.java
- banco-legacy-account-service/src/main/resources/application.properties
- banco-legacy-account-service/src/main/resources/schema-eft-account.sql
- banco-legacy-account-service/src/test/java/com/duoc/banco_legacy/account/AccountFinancialTests.java
- banco-legacy-account-service/src/test/java/com/duoc/banco_legacy/account/AccountRegistryIntegrationTests.java
- banco-legacy-account-service/src/test/java/com/duoc/banco_legacy/account/CustomerRegistryClientTests.java
- banco-legacy-account-service/src/test/java/com/duoc/banco_legacy/account/FinancialOutboxTests.java
- banco-legacy-account-service/src/test/resources/application.properties
- banco-legacy-auth/src/main/java/com/duoc/banco_legacy/auth/AuthorizationServerConfig.java
- banco-legacy-auth/src/main/resources/application.properties
- banco-legacy-auth/src/test/java/com/duoc/banco_legacy/auth/PaymentOperatorAuthorizationTests.java
- banco-legacy-core/src/main/java/com/duoc/banco_legacy/core/event/FinancialOperationCompletedEvent.java
- banco-legacy-core/src/main/java/com/duoc/banco_legacy/core/event/FinancialOperationRequest.java
- banco-legacy-core/src/main/java/com/duoc/banco_legacy/core/event/FinancialOperationResult.java
- banco-legacy-core/src/test/java/com/duoc/banco_legacy/core/event/FinancialContractTests.java
- banco-legacy-payment-service/pom.xml
- banco-legacy-payment-service/src/main/java/com/duoc/banco_legacy/payment/domain/AccountPostingClient.java
- banco-legacy-payment-service/src/main/java/com/duoc/banco_legacy/payment/domain/FinancialConsumerConfig.java
- banco-legacy-payment-service/src/main/java/com/duoc/banco_legacy/payment/domain/FinancialEventConsumer.java
- banco-legacy-payment-service/src/main/java/com/duoc/banco_legacy/payment/domain/PaymentController.java
- banco-legacy-payment-service/src/main/java/com/duoc/banco_legacy/payment/domain/PaymentErrorHandler.java
- banco-legacy-payment-service/src/main/java/com/duoc/banco_legacy/payment/domain/PaymentException.java
- banco-legacy-payment-service/src/main/java/com/duoc/banco_legacy/payment/domain/PaymentRemoteConfig.java
- banco-legacy-payment-service/src/main/java/com/duoc/banco_legacy/payment/domain/PaymentStore.java
- banco-legacy-payment-service/src/main/java/com/duoc/banco_legacy/payment/security/PaymentSecurityConfig.java
- banco-legacy-payment-service/src/main/resources/application.properties
- banco-legacy-payment-service/src/main/resources/schema-eft-payment.sql
- banco-legacy-payment-service/src/test/java/com/duoc/banco_legacy/payment/AccountPostingClientTests.java
- banco-legacy-payment-service/src/test/java/com/duoc/banco_legacy/payment/FinancialKafkaTests.java
- banco-legacy-payment-service/src/test/java/com/duoc/banco_legacy/payment/JwtTestSupport.java
- banco-legacy-payment-service/src/test/java/com/duoc/banco_legacy/payment/PaymentFinancialTests.java
- banco-legacy-payment-service/src/test/resources/application.properties
- config-repository/banco-legacy-payment-service.yml
- docker-compose.yaml
- docker/compose.eft.yaml
- docs/eft/contratos-servicios.md
- docs/eft/informe-etapa-3.md
- docs/eft/modelo-dominio.md
- docs/eft/plan-implementacion.md
- docs/eft/validacion-etapa3.txt
- scripts/initialize-compose-environment.ps1
