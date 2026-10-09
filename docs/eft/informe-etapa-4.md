# Informe Etapa 4 — validación operacional real

Fecha: 4 de octubre de 2026. Base d046500, rama eft.
Validación ejecutada con Docker Compose, PostgreSQL, HTTPS verificado, OAuth real y Kafka Compose. Sin push, merge, cloud ni escalado.

## Entorno

- Docker cliente/Engine 29.8.1, Docker Desktop 4.93.0, Compose v5.5.1.
- Engine Linux/amd64 sobre WSL2, contexto desktop-linux.
- PostgreSQL 16.4, banco_legacy_batch, usuario técnico postgres; psql del contenedor, host5433→5432.
- Volumen banco-legacy_postgres-data en /var/lib/postgresql/data, preservado. Sin down -v, borrados, truncados ni SQL de reparación.
- Kafka 3.8.0, imagen apache/kafka:3.8.0. Broker de Compose real, distinto del broker embebido de los tests.
- Infraestructura postgres/kafka/config-server/discovery-server/auth-server levantada primero; después customer-service/account-service/payment-service.
- Ocho servicios running/healthy al cierre, restartCount0 en la captura final.
- Compose base y base+override EFT pasan config --quiet.
- Contenedor huérfano banco-legacy-batch-import detectado y conservado; no se usó remove-orphans.
- Servicios BFF existentes no reconstruidos, levantados ni integrados con maestros modernos en esta etapa.

## Correcciones y diagnóstico

Account reiniciaba porque la imagen desplegada anterior no incluía BOOT-INF/classes/schema-eft-account.sql mientras Config Server ya requería ese recurso.
Se comprobó el JAR antiguo y se capturó el error exacto No schema scripts found at location classpath:schema-eft-account.sql.
Se reconstruyeron Account, Customer, Payment y Auth desde eft. El JAR Account actual contiene el SQL y el contenedor quedó sano.
No se cambió Java, SQL, configuración de negocio ni se desactivó la inicialización; no se crearon tablas manualmente.

Se ejecutó el inicializador existente sin RotateSecrets. Los valores previos de .env y la pareja JWT se conservaron; se completaron credenciales faltantes, incluido OAUTH_PAYMENT_CLIENT_SECRET.
El certificado anterior no estaba vencido, pero sus SAN no incluían customer-service/payment-service. El inicializador regeneró TLS para cubrir esos nombres, conservando la contraseña existente.
La primera comprobación de igualdad TLS detectó esa regeneración y se investigó mediante el certificado servido por Auth aún en memoria. La segunda ejecución preservó .env y todos los archivos TLS.
No se afirma que se conservara un certificado que no cumplía los nombres requeridos.
JWT, secretos OAuth, contraseñas y tokens no aparecen en las evidencias.

El runner tuvo un error de impresión Unicode al finalizar la fase de resiliencia en Windows, después de completar sus aserciones. Se configuró stdout UTF-8 y se recomprobaron los resultados read-only, sin repetir movimientos.
También se hizo la lectura DLT repetible: filtra el marcador de esta ejecución y acepta el timeout acotado del lector si el mensaje esperado está presente.

## Esquema PostgreSQL real

Inicializado por los scripts versionados al arrancar cada servicio:
- Account: eft_account, eft_account_holder, eft_account_balance, eft_account_posting, eft_financial_outbox.
- Customer: eft_customer.
- Payment: eft_payment_operation, eft_payment_event_audit.

Las ocho tablas existen en public. Las tablas Batch/anomalías previas siguen presentes.
Todas las comprobaciones SQL de la validación usan BEGIN READ ONLY; los cambios de prueba se realizaron mediante APIs y eventos, no SQL directo.
OPEN físico continúa representándose ACTIVE en la API, conforme a Etapa3.

## OAuth y seguridad

Authorization Server real en HTTPSlocalhost8084, client_credentials.
- banco-domain-operator: accounts.read/accounts.write/customers.read/customers.write.
- banco-payment-operator: payments.read/payments.write/accounts.post/accounts.post.read.
Ambos obtuvieron HTTP200; access_token redactado en archivos.
Se verifican certificado y hostname mediante CA/certificado local, sin --insecure en el runner.
Token financiero válido permitió ejecutar operaciones. Sin token401; token administrativo sin payments.write403.
Identidad OAuth sigue siendo cliente técnico; no se implementó IAM de persona ni autorización final por titularidad.

## Customer real

Customer neutral d4d5cf63-c6c8-4cb5-b6cc-5154ac3a95ad, nombre de fixture Prueba EFT 4; sin PII ficticia añadida.
PUT201, GET200 y PATCH200; nombre Prueba EFT 4 actualizada, versión1 confirmada en PostgreSQL.
Registro conservado; no eliminación física ni carga desde legacy.

## Account real

| Cuenta de prueba | Apertura | Saldo inicial | Estado final API / DB | Saldo final | Versión final |
|---|---|---:|---|---:|---:|
| 1791148331085 | 201, titular explícito | 0 | ACTIVE / OPEN | 79 | 5 |
| 1791148331086 | 201, titular explícito | 0 | CLOSED / CLOSED | 20 | 3 |

customerId singular y customerIds fueron comprobados. Consulta por customerId devolvió ambas cuentas.
Mantenimiento con versión0 retornó versión1 en origen; los movimientos incrementaron la versión existente.
Cierre retuvo saldo20/registro/titular. Depósito sobre cerrada409 ACCOUNT_CLOSED.
Transferencia hacia cerrada409 sin débito parcial ni posting; Payment registró FAILED.
No se usaron saldos ni titulares legacy.

## Payment e idempotencia

| Flujo | Resultado real |
|---|---|
| Depósito100 a origen | 201 COMPLETED, saldo100 |
| Transferencia30 origen→destino | 201 COMPLETED, saldos70/30, receipt real |
| Pago10 desde destino | 201 COMPLETED, saldos70/20; débito registrado sin merchant |
| Replay depósito/transferencia/pago | 200, misma operación y respuesta, tres postings/operaciones iniciales |
| Misma clave con importe distinto | 409 IDEMPOTENCY_CONFLICT, sin otro movimiento |
| Depósito cerrado / transferencia a cerrado | 409, dos FAILED, sin postings |
| Recuperación HTTP depósito7 | 200, COMPLETED una vez, origen77 |
| Recuperación Kafka depósito2 | Account201, Payment reconciliado sin retry previo; replay200, origen79 |

Prefix de prueba: eft4-a1f2ee5aa5ef.
Estado PostgreSQL final: siete operaciones Payment, cinco COMPLETED y dos FAILED; cinco postings, sin PENDING de esta ejecución.
Los comprobantes iniciales se mantuvieron inmutables en replay aunque después cambiara el saldo.
Transferencia debitó/acreditó dentro de la transacción Account; no se afirma ACID entre Payment y Account.

## Resilience4j real

Se confirmó GET de operación normal200; después se detuvo solo account-service.
Payment devolvió503 ACCOUNT_UNAVAILABLE, también en repeticiones. PostgreSQL mostró PENDING/receipt null y cero postings para esas claves.
Se restauró Account en finally, sin tocar otros servicios ni volumen.
Tras health healthy, el primer reintento observado recuperó la solicitud con HTTP200; replay200, un posting y solo7 de crédito.
Se creó además una solicitud PENDING durante la caída. Después de restaurar Account se ejecutó su comando interno idempotente real con el mismo operationId/key/actor.
El evento Kafka completado reconcilió Payment antes de repetir su POST; replay devolvió el comprobante sin otro crédito. Esta prueba no simula pérdida HTTP ni escribe PENDING artificialmente por SQL.
La apertura/HALF_OPEN interna del circuito sigue cubierta explícitamente por tests; la evidencia real demuestra caída, fallback503, ausencia de falso éxito y recuperación.

## Kafka real

Topic banco.operaciones.completadas.v1, tres particiones, réplica1 local.
Cinco outboxes creadas en PostgreSQL terminaron PUBLISHED, con attempts1 y published_at; cinco eventos auditados.
Publicador configura acks=all y marca PUBLISHED tras la confirmación, conforme al código probado de Etapa3.
Grupo financial-payment-audit consumió el topic; lag0 en las tres particiones capturadas.
Republicar un evento real con el mismo eventId mantuvo una sola fila de auditoría, sin nuevos saldos/postings.
La reconciliación PENDING por evento se verificó en ejecución real, separadamente del retry HTTP.
Se inyectó solo un JSON malformado identificado por el prefix de prueba. Llegó a banco.operaciones.completadas.v1.DLT.
Ese error es no reintentable por configuración. No se forzó una caída de DB/Kafka compartidos para demostrar reintentos transitorios; permanecen cubiertos por tests.
Después de DLT se republicó un evento válido y se verificó consumo/lag0 y deduplicación; Payment siguió respondiendo200.
La lectura DLT acotada puede terminar con TimeoutException al no alcanzar el máximo solicitado; se verificó el mensaje esperado, no se interpretó el timeout como ausencia de DLT.
No se modificó el flujo de anomalías ni se inyectaron mensajes en su topic.
Entrega al menos una vez, sin afirmar exactly-once.

## PostgreSQL: resultados relevantes

Customer versión1, origen balance79/version5, destino CLOSED balance20/version3.
Payment5 COMPLETED/2 FAILED; posting5; outbox5 PUBLISHED; audit5.
Consultas de tablas, relaciones, balances, operaciones y audit quedan en las evidencias JSON. El detalle22 incluye titulares, request/receipt persistidos, cinco outboxes/audits y comprueba igualdad exacta de comprobantes Account/Payment. Los datos de prueba se conservan para revisión.
No se corrigieron resultados mediante INSERT/UPDATE/DELETE SQL.

## Tests

Focalizados Core/Account/Payment/Customer/Auth: BUILD SUCCESS.
mvn verify final: BUILD SUCCESS, 245 tests reportados, 0 fallos, 0 errores, 0 skipped reportados.
Finalizó 2026-10-04T18:19:26-03:00, tiempo3:13.
Los cuatro KafkaReal heredados siguen sin habilitarse y retornan sin broker externo; no se presentan como ejecutados. La validación financiera Kafka Compose real sí se ejecutó mediante el runner y se documenta aparte.

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

## Evidencias y reproducción

Archivos JSON/TXT derivados de HTTP real, psql, Docker y Kafka CLI, con timestamp y valores sensibles redactados. No se presentan como capturas gráficas.
Runner scripts/validate-eft-compose.py usa stdlib Python y herramientas disponibles dentro de contenedores.
Después de preparar/build/health del entorno, ejecutar desde raíz las fases baseline, resilience y kafka, en ese orden, una vez por runId.
baseline crea fixtures nuevos; resilience detiene solo Account y lo restaura; kafka reproduce un evento y provoca JSON inválido controlado.
No limpiar directamente las tablas ni repetir fases a mitad de una ejecución ya completada. Credenciales solo en memoria/stdin, nunca argumentos CLI ni archivos de evidencia.
TLS/hostname se validan; .env y stores locales quedan ignorados por Git.

- docs/evidence/eft/01-entorno-inicializador.json
- docs/evidence/historico/eft/02-diagnostico-account.json
- docs/evidence/eft/03-compose-health-esquema.json
- docs/evidence/eft/04-oauth-domain.json
- docs/evidence/eft/04-oauth-payment.json
- docs/evidence/eft/05-customer.json
- docs/evidence/eft/06-account-apertura.json
- docs/evidence/eft/07-payment-deposit.json
- docs/evidence/eft/07-payment-payment.json
- docs/evidence/eft/07-payment-transfer.json
- docs/evidence/eft/08-idempotencia.json
- docs/evidence/eft/09-seguridad.json
- docs/evidence/eft/10-cierre-rollback.json
- docs/evidence/eft/11-resilience-503.json
- docs/evidence/eft/12-resilience-recuperacion.json
- docs/evidence/eft/12b-kafka-reconciliacion-pending.json
- docs/evidence/eft/13-kafka-outbox-audit.json
- docs/evidence/eft/14-kafka-dlt.json
- docs/evidence/eft/15-kafka-continuidad.json
- docs/evidence/eft/16-postgresql-final.json
- docs/evidence/eft/17-compose-final-health.json
- docs/evidence/eft/18-resilience-recomprobacion.json
- docs/evidence/historico/eft/19-bff-tls-fuera-alcance.json
- docs/evidence/eft/20-entorno-versiones.json
- docs/evidence/historico/eft/21-mvn-verify-final.txt
- docs/evidence/eft/22-postgresql-detalle.json

## Git

Base d046500, rama eft. Commits locales:
- 3e12478 — Agrega validación reproducible EFT sobre Compose real.
- 25ee7c1 — Registra evidencia real PostgreSQL HTTPS OAuth y Kafka de Etapa 4.
- Cierre documental: commit que contiene este informe, titulado Documenta cierre Etapa 4 y pendientes de validación BFF.

git status --porcelain vacío tras el cierre documental; main permanece76b9773.
29 archivos versionados respecto de d046500: runner, informe, plan y las26 evidencias listadas arriba.
Consultar git log -3 para el identificador del propio commit documental.
Sin push, merge, cloud ni demostración de escalado.
Versionados solo runner, informe/evidencias y actualización del plan. .env, claves, stores y logs locales no se incluyen.

## Pendientes reales

1. Los tres BFF existentes aún sirven el certificado anterior. Una consulta TLS read-only contra la CA actual confirma el fallo de confianza en8081/8082/8083. Recargar/reiniciar esos procesos antes de validar compatibilidad HTTPS BFF; no se integraron ni cambiaron en esta etapa.
2. Integración de BFF con cuentas modernas/autorización de personas y migración/reserva de IDs legacy siguen fuera de alcance, sujetos a decisiones explícitas.
3. Reintentos transitorios reales ante caída del broker/DB no se forzaron; cubiertos por tests. La DLT y la continuidad real sí se comprobaron.
4. Antes de escalado, coordinar selección de outbox y validar despliegue concurrente; no escalar ahora.

## Siguiente etapa recomendada

Recargar TLS de BFF y verificar primero sus contratos existentes. Después, definir la integración de canales con los maestros modernos y su autorización sin inferir identidades desde legacy.
Preparar escalado coordinando outbox y puertos solamente cuando se autorice. Cloud/entregables finales permanecen posteriores.
