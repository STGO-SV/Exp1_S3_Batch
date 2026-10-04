# EFT — cierre de Etapa 2

## Resultado y alcance

Diseño basado en los nueve CSV y código/esquema reales; Account y Customer registrales implementados, sin seeding ni saldos/titulares inventados. Payment financiero se detuvo en diseño por decisiones que requieren revisión humana.
No afirmar gestión financiera integral: apertura/cierre nuevos son administrativos y no habilitan ni liquidan saldos, ni cambian el retiro legacy.
No push, merge a main, escalado ni despliegue cloud.

## Estado Git

- Rama eft.
- Base de esta etapa 9c19005; main permanece 76b9773.
- 2cf83fe: modelo real, evidencia agregada y contratos.
- 284f6b0: maestros registrales, scopes/resiliencia, persistencia aditiva y Compose.
- Tercer commit de cierre documental: hash entregado en respuesta final y git log.
- git status --short sin salida al cerrar: árbol limpio, comprobado después del commit final.

## Modelo real encontrado

Cuenta: cuenta_id BIGINT/Long en intereses y movimientos. id SQL es identificador de fila, no cuenta.
Intereses: resultados derivados con nombre/saldo_original/tasa/saldo_procesado/tipo, sin periodo; última fila por id DESC usada por consultas.
Titulares: solo nombre/edad en entrada; edad no persistida. No customer_id, documento, contacto ni identidad de usuario final.
Semana 3: 1000 filas por archivo, 50 claves de cuenta; cada clave tiene nombres contradictorios. No convertir estos nombres en clientes ni asociaciones fiables.
Movimientos: cuenta_id, fecha, tipo, monto, descripción; sin identificador de origen. compra/deposito/retiro admitidos; pago/depósito existen en entrada pero no son tipos admitidos por el processor.
Transacciones: id/fecha/monto/debito o credito; NO cuenta_id, origen/destino o comercio. Anomalías siguen transaction_id; no relacionarlas por igualdad de números con cuenta_id.
Cliente multicuenta/cotitularidad: no demostrable por dataset; nuevo registro admite declaraciones explícitas sin deducirlas.
PostgreSQL vivo: inspección de solo lectura no pudo ejecutarse porque Docker Desktop Linux engine está detenido. Se analizó el esquema versionado, no se afirmó revisar datos vivos.

## Account Service

Inicialmente: consultas GET sobre resultados Batch, datasource read-only, sin maestro ni lifecycle.
Ahora: eft_account y eft_account_holder separados; fuente de verdad para metadatos/vínculos explícitos. No saldo ni ledger.
Endpoints:
- PUT /api/accounts/{id}: alta registral, verifica clientes remotos, replay idéntico; conflicto si ID legacy.
- GET /api/accounts/{id}: metadatos y customerIds.
- PATCH /api/accounts/{id}: clasificación con versión.
- POST /api/accounts/{id}/closure: cierre registral/versionado/idempotente.
- GET /api/accounts?customerId=&limit=&offset=: vínculos paginados.

Una transacción local para maestro/vínculos; rollback de cuenta y primer vínculo si falla el siguiente. Compare-and-set protege mantenimiento/cierre; prueba concurrente acepta exactamente un escritor por versión.
Guard de claves legacy devuelve 409; no migración silenciosa ni cambio de titulares por nombre.
Security conserva ROLE_* para consultas antiguas y requiere scopes en rutas nuevas.
Tests: 21 de módulo (15 nuevos: 10 registro + 5 cliente remoto). GET BFF y retiro legacy conservados.
Pendientes: saldo operativo, importación validada, reserva de IDs, cierre financiero/ATM y autorización por titularidad.

## Customer Service

Modelo eft_customer: UUID técnico explícito/name/version; nombre no único. No datos personales inventados ni datos demo productivos.
Endpoints:
- PUT /api/customers/{id}: crea por UUID declarado o replay; distinto contenido para mismo ID da 409.
- GET /api/customers/{id}: registro.
- PATCH /api/customers/{id}: nombre con versión.
- GET /api/customers/{id}/accounts: existe cliente, consulta real Account y relay JWT, paginación.

Persistencia JDBC en tabla propia; sin eliminación ni acceso directo a tablas Account.
Vínculos pertenecen a Account; no se denormaliza nombre ni se asume sub OAuth=customer_id.
Security: customers.read/write; listar cuentas requiere además accounts.read.
Tests: 16 de módulo (13 nuevos: 8 registro + 5 cliente), incluidos conflictos, UUID/body inválidos, schema reaplicado, concurrencia, relación remota/fallo y permisos.

## Payment Service

Sigue scaffolding de Etapa 1. No funciones financieras nuevas ni nuevas tablas de ledger/pago.
Contratos propuestos para depósito, pago académico como débito, transferencia y consulta de comprobante; posting atómico en Account y consulta idempotente interna también documentados.
Saldo suficiente/bloqueo/rollback de ATM son patrones existentes reutilizables, no prueba de Payment completo.
Consistencia futura: débito y crédito juntos con movimientos/comprobante/outbox; no dos llamadas de saldo ni crédito eventual por Kafka.
Idempotencia futura: identidad JWT validada + tipo + key + hash del payload; retorno original, 409 si cambia payload; timeout se reconcilia por misma clave.
Sin nueva prueba de transferencia/pago/saldo insuficiente: no lógica implementada. Los tests existentes de retiro/saldo insuficiente siguen ejecutándose.
Se detuvo para revisar titular/snapshot, saldo maestro, cierre y significado académico de pago; no añadió moneda/tasa/comisión/límite financiero.

## Kafka

Implementado anteriormente: anomalías Batch → outbox → Kafka → Anomaly, retry/DLT y dedup por event_id y transaction_id único.
Etapa 2: ningún evento nuevo artificial.
Propuestos DepositCompleted/PaymentCompleted/TransferCompleted para proyección y reconciliación después del commit financiero; no para volver a mover saldo. CustomerUpdated/AccountClosed solo si hay consumidor concreto.
Outbox de anomalías no generalizado: sus columnas son específicas. Futuro outbox de dominio separado conservará la infraestructura/patrón, con claim/lease para múltiples publishers.
Los cuatro tests KafkaReal* retornan anticipadamente; no evidencia Kafka externo. Tests embedded sí incluidos en verify.

## Resilience4j

Dos dependencias reales implementadas:
- Account → Customer: validar existencia de titulares declarados; 404 desconocido, 403 permiso remoto, 503 indisponibilidad; no insertar en fallback.
- Customer → Account: consultar relaciones; 403/503 controlados; nunca sustituir caída por lista vacía.

Connect/read timeouts y circuit breaker; 4xx no cuentan como caída en configuración. Tests comprueban relay, apertura del circuito, rechazo y ausencia de resultado ficticio.
Payment → Account es diseño futuro, no dependencia implementada.

## OAuth2 y secretos

Se reutilizan Auth y validación JWT RSA/issuer/audience/firma/tiempo.
Cliente técnico opcional banco-domain-operator, ROLE_DOMAIN_OPERATOR, scopes accounts.read/write y customers.read/write. Sin permisos de canal ni Payment.
WEB/MOBILE/ATM no obtienen permisos administrativos por ser canales; tests de emisión/grants lo comprueban.
No autenticación de cliente final ni autorización de propiedad inventada; vínculos declarados no equivalen a identidad acreditada.

Inicializador agrega/reutiliza secreto externo del operador y actualiza SAN Customer/Payment conservando secretos anteriores. Se probó upgrade y segunda ejecución idéntica en fixture aislada que luego se eliminó.
No se tocó .env/.local de trabajo; no se versionaron secretos/certificados privados.

## Docker

Customer funcional se integró al docker-compose.yaml base: PostgreSQL/Config/Eureka, HTTPS/truststore/health, sin puerto host.
Account usa maestro writable con SQL init solo aditivo; mantiene puerto/GET legacy.
Payment continúa opcional bajo perfil eft. No declarar tres microservicios de negocio completos.
Base y mezcla de overrides validan con config --quiet.
No se construyeron ni arrancaron contenedores: Docker daemon detenido. No escalado ni cloud.
Procedimiento de actualización TLS/persistencia: persistencia-operacion-etapa2.md.

## Tests

mvn -B verify: BUILD SUCCESS en todos los módulos, 03:00, finalizó 2026-10-04 15:35:35 -03:00.
207 casos reportados; cero fallos/errores/skips. 30 casos nuevos.
Cuatro casos de Kafka externo no ejecutaron comprobaciones de integración; no confundir el total Surefire con esa evidencia.
H2 MODE=PostgreSQL para nuevas tablas/transacciones; no prueba PostgreSQL real.
Auth focalizado: 9 casos, BUILD SUCCESS. Account/Customer focalizados pasaron; se corrigieron dos incompatibilidades de dependencias/API durante desarrollo y verify final confirma solución.
Inicializador aislado y Compose config: PASS. git diff --check: PASS.
Detalle por módulo y límites: validacion-etapa2.txt.

## Archivos modificados o creados en esta etapa

Lista completa respecto de 9c19005, incluyendo este informe:

- .env.example
- banco-legacy-account-service/pom.xml
- banco-legacy-account-service/src/main/java/com/duoc/banco_legacy/account/registry/AccountRegistryController.java
- banco-legacy-account-service/src/main/java/com/duoc/banco_legacy/account/registry/AccountRegistryService.java
- banco-legacy-account-service/src/main/java/com/duoc/banco_legacy/account/registry/CustomerRegistryClient.java
- banco-legacy-account-service/src/main/java/com/duoc/banco_legacy/account/registry/RegistryErrorHandler.java
- banco-legacy-account-service/src/main/java/com/duoc/banco_legacy/account/registry/RegistryException.java
- banco-legacy-account-service/src/main/java/com/duoc/banco_legacy/account/registry/RegistryRemoteClientConfig.java
- banco-legacy-account-service/src/main/java/com/duoc/banco_legacy/account/security/AccountSecurityConfig.java
- banco-legacy-account-service/src/main/resources/schema-eft-account.sql
- banco-legacy-account-service/src/test/java/com/duoc/banco_legacy/account/AccountRegistryIntegrationTests.java
- banco-legacy-account-service/src/test/java/com/duoc/banco_legacy/account/CustomerRegistryClientTests.java
- banco-legacy-account-service/src/test/java/com/duoc/banco_legacy/account/JwtTestSupport.java
- banco-legacy-auth/src/main/java/com/duoc/banco_legacy/auth/AuthorizationServerConfig.java
- banco-legacy-auth/src/main/resources/application.properties
- banco-legacy-auth/src/test/java/com/duoc/banco_legacy/auth/DomainOperatorAuthorizationTests.java
- banco-legacy-customer-service/pom.xml
- banco-legacy-customer-service/src/main/java/com/duoc/banco_legacy/customer/registry/AccountRegistryClient.java
- banco-legacy-customer-service/src/main/java/com/duoc/banco_legacy/customer/registry/CustomerRegistryController.java
- banco-legacy-customer-service/src/main/java/com/duoc/banco_legacy/customer/registry/CustomerRegistryService.java
- banco-legacy-customer-service/src/main/java/com/duoc/banco_legacy/customer/registry/RegistryErrorHandler.java
- banco-legacy-customer-service/src/main/java/com/duoc/banco_legacy/customer/registry/RegistryException.java
- banco-legacy-customer-service/src/main/java/com/duoc/banco_legacy/customer/registry/RegistryRemoteClientConfig.java
- banco-legacy-customer-service/src/main/java/com/duoc/banco_legacy/customer/security/CustomerSecurityConfig.java
- banco-legacy-customer-service/src/main/resources/application.properties
- banco-legacy-customer-service/src/main/resources/schema-eft-customer.sql
- banco-legacy-customer-service/src/test/java/com/duoc/banco_legacy/customer/AccountRegistryClientTests.java
- banco-legacy-customer-service/src/test/java/com/duoc/banco_legacy/customer/CustomerRegistryIntegrationTests.java
- banco-legacy-customer-service/src/test/java/com/duoc/banco_legacy/customer/JwtTestSupport.java
- banco-legacy-customer-service/src/test/resources/application.properties
- config-repository/banco-legacy-account-service.yml
- config-repository/banco-legacy-customer-service.yml
- docker-compose.yaml
- docker/compose.eft.yaml
- docs/eft/contratos-servicios.md
- docs/eft/datos-legacy-etapa2.json
- docs/eft/informe-etapa-2.md
- docs/eft/modelo-dominio.md
- docs/eft/persistencia-operacion-etapa2.md
- docs/eft/plan-implementacion.md
- docs/eft/validacion-etapa2.txt
- scripts/initialize-compose-environment.ps1

## Decisiones que requieren revisión humana

1. Saldo maestro/snapshot/periodo y convivencia con retiro ATM; no usar id DESC como fecha de negocio sin validar.
2. Identidades y titulares reales frente a nombres contradictorios; no deduplicar por nombre.
3. Reserva de IDs/migración de claves legacy y qué hacer si un CSV futuro colisiona con cuenta nueva.
4. Apertura/cierre/mantenimiento financieros, préstamos y bloqueo de operaciones en todos los canales.
5. Interpretación de pago como débito académico, ledger y comprobante/reconciliación; moneda solo si se define.
6. Autenticación final/delegación y autorización por titularidad; actual OAuth es técnico.
7. Consumidor/proyección que justifique eventos nuevos.
8. AWS y plantilla PDF según aclaraciones docentes previas.

## Siguiente etapa recomendada

Revisar 1–6 antes de implementar Payment o migrar saldo; después validar migraciones y nuevos contratos con PostgreSQL/Docker local. Solo entonces implementar posting financiero/idempotencia/outbox y probar transferencia/rollback/insuficiencia.
La etapa de escalado/cloud/PDF/video sigue posterior; no saltar a ella por disponer de scaffolding.