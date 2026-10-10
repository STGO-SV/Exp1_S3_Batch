# Etapa 6: escalabilidad horizontal real

> Documento histórico de trazabilidad interna. Estado final (2026-10-10): desarrollo técnico completo; EFT integrada en main y publicada en GitHub. Despliegue en entorno AWS EC2 ejecutado y documentado; capturas finales incorporadas. Informe PDF generado con la plantilla oficial y DOCX editable disponible. Video grabado y entregado fuera del repositorio, sin enlace alojado en este árbol.

Fecha local: 2026-10-04, America/Santiago. Rama `eft`; base `bacd0ba`.
Auditoría previa: [auditoria-etapa-6-escalabilidad.md](auditoria-etapa-6-escalabilidad.md).

## Resultado

Demostradas dos réplicas simultáneas healthy de Customer, Account y Payment, con IDs/IPs distintos en Eureka, HTTPS verificado, OAuth, routing efectivo, PostgreSQL compartido, outbox coordinada e idempotencia. Kafka repartió tres particiones entre dos Payment: lag final 0 y auditoría lógica deduplicada.

Se completaron failovers de una réplica por servicio y se regresó al Compose normal 1+1+1. Los BFF conservaron una réplica y exactamente sus respuestas legacy de Etapa 5. No hubo push, merge, cloud, eliminación de volúmenes ni reset de datos.

## 1. Bloqueos y cambios mínimos

| Bloqueo real | Solución |
|---|---|
| Publisher Account seleccionaba PENDING sin coordinación entre JVM | Claim PostgreSQL, lease y token de ownership |
| Puerto HOST 8085 impediría Account=2 | ports: !reset [] exclusivamente en override |
| Hostname compartido no identifica el destino de discovery | IP por réplica e instance-id aplicación/HOSTNAME |
| Certificado local identifica DNS, no IPs efímeras | Transporte verifica DNS lógico y cadena del truststore al conectar a la IP seleccionada |
| Failover real reveló una ruta obsoleta cacheada en Payment | Eliminar segunda caché LoadBalancer en override y habilitar Spring Retry idempotente Payment |

Se reutilizaron Eureka/Spring Cloud LoadBalancer y PostgreSQL/Kafka. No se añadieron gateways, proxies, endpoints de diagnóstico ni campos de instancia a respuestas de negocio.

Archivos:

- `docker/compose.scale.yaml`: puertos, IPs/IDs, health TLS, caché/retry y access logs.
- `FinancialOutboxStore.java` y `FinancialOutboxPublisher.java`: coordinación y confirmación.
- `schema-eft-outbox-lease.sql` y `schema-eft-account.sql`: extensión aditiva.
- `config-repository/banco-legacy-account-service.yml`: carga de migración.
- `ServiceTlsRequestFactory.java` y configuraciones HTTP de seis clientes: transporte para discovery por IP.
- POM Core/Customer/Web/Mobile: transporte compartido; Web/Mobile excluyen JDBC para mantener BFF sin DataSource.
- POM Payment y `LoadBalancedRetryTests.java`: Spring Retry con POST seguro.
- `scripts/validate-eft-scale.py` y `scripts/test-eft-scale.ps1`: validación reproducible y evidencia redactada.

`docker-compose.yaml` permanece sin cambios. No cambiaron contratos, retiro ATM, titularidad ni integración BFF con maestros modernos.

## 2. Puertos y Compose base

Puertos internos Customer/Account/Payment: 8087/8085/8086. El override no publica HOST ports ni define container_name para ellos. El binding 8085:8085 sigue en el archivo base y volvió a verificarse por HTTPS al retornar a singleton.

El runner usa la red existente mediante curl dentro de Web BFF. No se escalaron BFF, Auth, Config, Eureka, PostgreSQL ni Kafka. Los BFF se reconstruyeron/recrearon para cargar transporte TLS compatible; conservan una réplica.

IDs de PostgreSQL/Kafka/Auth/Config/Eureka/Batch/Anomaly preservados durante toda la etapa. Volumen `banco-legacy_postgres-data` intacto.

## 3. Eureka y estado

Instance-id efectivo: `${spring.application.name}:${HOSTNAME}`; prefer-ip-address=true. Compose escapa los signos dólar para que Spring resuelva las propiedades dentro del contenedor. Fuera de Docker se conservan los defaults.

Los tres negocios usan sesión STATELESS y PostgreSQL autoritativo. Customer tiene versionado optimista; Account mantiene locks de balances ordenados y UNIQUE(actor,idempotency_key); Payment mantiene operaciones/auditoría transaccionales y PK eventId. Los circuit breakers/cachés JVM no contienen el estado financiero.

Eureka del escenario corregido:

| Servicio | Instance ID | IP | HTTPS | Estado |
|---|---|---|---|---|
| customer-service | `banco-legacy-customer-service:813eebb19f99` | 172.20.0.10 | 8087 | UP |
| customer-service | `banco-legacy-customer-service:57570c1c0ab0` | 172.20.0.7 | 8087 | UP |
| account-service | `banco-legacy-account-service:28e09dae1a23` | 172.20.0.12 | 8085 | UP |
| account-service | `banco-legacy-account-service:833dd5da93f0` | 172.20.0.6 | 8085 | UP |
| payment-service | `banco-legacy-payment-service:5d45da48842c` | 172.20.0.16 | 8086 | UP |
| payment-service | `banco-legacy-payment-service:d2f4b4fdeb95` | 172.20.0.17 | 8086 | UP |

## 4. Routing y HTTPS

Payment → Account, Account → Customer, Customer → Account y BFF → Account ya utilizan RestClient.Builder @LoadBalanced con service-id. Anunciar IPs individuales permite alcanzar la instancia seleccionada sin depender de una IP cacheada del DNS compartido.

ServiceTlsRequestFactory usa SSLContexts.createSystemDefault y DefaultHostnameVerifier: valida cadena mediante truststore JVM vigente y SAN del DNS lógico configurado. No usa trust-all ni un verificador permisivo. Si la propiedad está vacía, verifica normalmente el hostname de la URL. No se regeneraron certificados ni rotaron secretos. [DefaultHostnameVerifier](https://hc.apache.org/httpcomponents-client-5.5.x/current/httpclient5/apidocs/org/apache/hc/client5/http/ssl/DefaultHostnameVerifier.html).

El runner consulta Eureka UP, elige round-robin y usa curl --resolve para conectar a la IP conservando el DNS TLS esperado. Comprueba remote_ip y registra la instancia fuera de la respuesta de negocio. Es un cliente de pruebas, no un gateway de producción.

Ocho GET por servicio demostraron dos IPs reales y access logs 200 en ambos contenedores. Los logs /internal/accounts/postings también prueban que ambos Account atendieron llamadas desde Payment mediante Spring Cloud LoadBalancer. Las llamadas registrales Account → Customer respondieron correctamente con TLS por identidad de servicio.

## 5. Outbox / claim-lease

Migración PostgreSQL aditiva: claim_owner, claim_token, claimed_at, lease_until, retry_at e índice; check ampliado a PENDING/PROCESSING/PUBLISHED. La sustitución del check ocurre en BEGIN/COMMIT sin recrear tabla ni alterar filas históricas.

1. CTE con FOR UPDATE SKIP LOCKED y UPDATE RETURNING reclama un evento PENDING o PROCESSING cuya lease venció.
2. Asigna owner/token nuevos, reloj PostgreSQL, lease y attempts+1.
3. Termina la operación DB antes de esperar la red Kafka.
4. KafkaTemplate.send espera ack y solo entonces intenta PUBLISHED.
5. La confirmación exige PROCESSING, owner/token coincidentes y lease vigente.
6. Un fallo devuelve el claim propio a PENDING con retry diferido dos segundos. Otro worker recupera claims vencidos de procesos caídos.

Lease por defecto 30s; espera Kafka 5s; constructor rechaza leases inferiores a 15s. Se reclama justo antes de cada envío, sin reservar un lote que venza esperando red. En escala: batch-size=1 y delay=300ms para compartir backlog; base: delay=2000ms y batch-size=20.

El token impide que un worker anterior confirme un claim recuperado. No hay locks JVM ni publisher permanente. [PostgreSQL 16: bloqueo de filas/SKIP LOCKED](https://www.postgresql.org/docs/16/sql-select.html).

Semántica at-least-once: una caída tras ack y antes del update DB, o pausa extrema superior a la lease, puede volver a entregar el mismo eventId. El fencing DB no es fencing del broker. La deduplicación persistente Payment mantiene una proyección lógica única; no se afirma exactly-once físico en Kafka.

## 6. Kafka / Payment

Grupo `financial-payment-audit`; topic `banco.operaciones.completadas.v1` con tres particiones y RF1 sin cambios. Client IDs incorporan HOSTNAME. En la captura inicial un Payment recibió (0,1) y el otro (2); lag 0 en cada partición.

Se reenvió el mismo eventId/operationId/payload desde una outbox del nuevo run. El offset avanzó y la auditoría no aumentó. Payment usa PK eventId y reconciliación transaccional, sin depender de memoria JVM.

Al detener un Payment, el otro recibió las tres particiones y lag quedó 0. Al restaurar regresaron dos instancias. En el singleton final el consumidor restante atiende las tres y lag sigue 0.

## 7. Flujo financiero 2+2+2

Run nuevo `eft6-360e45c45f52`, sin keys de Etapa 4.

- Customer `5aa4893c-105b-4b4e-a6d6-29ee01eb58b6`.
- Origen `1791158116942` y destino `1791158116943`, abiertos por API.
- Depósito 100, transferencia 20 y pago 1.
- Replay de las tres keys desde otra réplica Payment: 200 y respuestas idénticas.
- Key de depósito con amount=101: 409 IDEMPOTENCY_CONFLICT.
- Veinte depósitos concurrentes de 1, keys nuevas: 201/COMPLETED.
- OAuth real client_credentials domain/financial; sin token 401 y scope incorrecto 403.

Antes de failover: 23 postings, 23 operaciones, 23 outboxes PUBLISHED y 23 audits; balances 99/20. Ambos publishers participaron y todos los eventos tuvieron attempts=1:

| Owner conservado | Eventos PUBLISHED |
|---|---:|
| `banco-legacy-account-service:1a2f026fc6b1:714f221f-0e13-4fd9-b3dc-9468556becfa` | 14 |
| `banco-legacy-account-service:1bc02f50c148:0856fefb-8de3-4034-b370-f5b4832b78b2` | 9 |

Dos depósitos de 1 adicionales durante failover Account/Payment completaron el run con 25 postings, 25 operaciones COMPLETED, 25 outboxes PUBLISHED y 25 audits; saldo origen 101/destino 20. No quedan PROCESSING/PENDING bloqueados en este run.

## 8. Failover y corrección real

Se detuvo exclusivamente el contenedor elegido, restaurado en finally. La otra réplica respondió 200 y Account/Payment permitieron operaciones financieras.

| Servicio | GET superviviente | POST durante caída | Tiempo hasta evidencia |
|---|---|---|---:|
| customer-service | 200 | No aplica | 12.82s |
| account-service | 200 | 200 | 25.62s |
| payment-service | 200 | 201 | 29.42s |

Los tiempos incluyen convergencia y procedimiento; no miden indisponibilidad ni constituyen un SLA.

La primera prueba Account produjo un 503 en Payment por ruta cacheada adicional, aunque Eureka ya convergía. La evidencia conserva el fallo y una operación PENDING sin posting.

Se corrigió cache.enabled=false en override y se reutilizó Spring Retry Payment: cero retries sobre la misma instancia, uno sobre otra, POST habilitado por sus keys idempotentes y status 503 retryable. El contrato se conserva. Un test con dos servidores verificó ocho POST exitosos y key/body intactos ante un 503. Soporte confirmado en [Spring Cloud Commons 4.1.4](https://raw.githubusercontent.com/spring-cloud/spring-cloud-commons/v4.1.4/spring-cloud-commons/src/main/java/org/springframework/cloud/client/loadbalancer/LoadBalancerAutoConfiguration.java).

La key `eft6-360e45c45f52-failover-account-service` completó el mismo operationId `6aea58e7-7b58-4e21-94f0-f06ff6c814e7`, devolvió 200 y creó un posting. No se borró/reemplazó la operación PENDING. Capturas iniciales y corregidas preservadas.

La ventana exacta muerte tras claim/antes de ack se cubrió con tests PostgreSQL reales. No se añadió pausa artificial ni se forzó corrupción del stack para producir una captura runtime.

## 9. PostgreSQL / preservación

Snapshot previo READ ONLY de once tablas. Comparación final de todas las columnas previas como multiconjunto, incluyendo duplicados; las columnas aditivas de outbox no forman parte del contenido histórico comparado. Se permiten las nuevas filas del run autorizado.

| Tabla | Antes | Después | Filas previas preservadas |
|---|---:|---:|---|
| eft_customer | 1 | 2 | Sí |
| eft_account | 2 | 4 | Sí |
| eft_account_holder | 2 | 4 | Sí |
| eft_account_balance | 2 | 4 | Sí |
| eft_account_posting | 5 | 30 | Sí |
| eft_payment_operation | 7 | 32 | Sí |
| eft_payment_event_audit | 5 | 30 | Sí |
| eft_financial_outbox | 5 | 30 | Sí |
| interes_procesado | 282 | 282 | Sí |
| movimiento_anual_procesado | 642 | 642 | Sí |
| transaccion_procesada | 401 | 401 | Sí |

PostgreSQL 16.4/volumen permanecen sanos. Verificación posterior al verify final: cero operaciones del run no COMPLETED, cero outboxes del run no PUBLISHED y cero schemas temporales de tests pendientes. No hubo reset ni borrado de volúmenes.

## 10. Tests / regresión final

- Iniciales: 198 focalizados y verify 255, BUILD SUCCESS antes de escalar.
- Tras retry: 199 focalizados y verify 256, BUILD SUCCESS antes del retest.
- Final después de base: 256 reportados, cero fallos/errores/skipped, BUILD SUCCESS; 2026-10-04 21:26:36 -03:00.
- Nuevos: seis PostgreSQL de claims/locking/lease/ownership/retry/migración; cuatro publisher (sustituyen uno), uno TLS y uno retry POST; incremento neto 11.
- PostgreSQL real habilitado con EFT_POSTGRES_TEST_URL; cada test crea/elimina solo su schema temporal eft6_test_UUID, sin modificar tablas de negocio public.
- Cuatro KafkaReal heredados retornan sin flag de broker externo; el broker Docker real se demuestra mediante runner/consumer group.

Web dashboard, Mobile summary y ATM balance de cuenta 101: 200 y respuestas completas idénticas a Etapa 5; 401 sin token y 403 con rol incorrecto. TLS vigente verificado. Customer/Account/Payment modernos 200. Account host 8085 volvió a responder 200 con HTTPS verificado.

## 11. Estado final / reproducción

Compose base: 1 Customer + 1 Account + 1 Payment, tres BFF singleton y los siete servicios restantes singleton, todos healthy. Se retiraron réplicas adicionales con compose up --scale=1, sin down ni eliminación de volúmenes.

Comandos probados: [despliegue.md](despliegue.md). El state local conserva runId/keys y evita repetir fixtures monetarias accidentalmente.

## 12. Evidencias

Todos en `docs/evidence/eft/`. Variantes inicial/corregida tienen timestamps reales.

- [04-oauth-etapa6-atm.json](../evidence/eft/04-oauth-etapa6-atm.json)
- [04-oauth-etapa6-domain.json](../evidence/eft/04-oauth-etapa6-domain.json)
- [04-oauth-etapa6-mobile.json](../evidence/eft/04-oauth-etapa6-mobile.json)
- [04-oauth-etapa6-payment.json](../evidence/eft/04-oauth-etapa6-payment.json)
- [04-oauth-etapa6-web.json](../evidence/eft/04-oauth-etapa6-web.json)
- [etapa6-01-preservacion-previa.json](../evidence/eft/etapa6-01-preservacion-previa.json)
- [etapa6-02-compose-scale-config-inicial.json](../evidence/historico/eft/etapa6-02-compose-scale-config-inicial.json)
- [etapa6-02-compose-scale-config.json](../evidence/eft/etapa6-02-compose-scale-config.json)
- [etapa6-03-replicas-docker-inicial.json](../evidence/historico/eft/etapa6-03-replicas-docker-inicial.json)
- [etapa6-03-replicas-docker.json](../evidence/eft/etapa6-03-replicas-docker.json)
- [etapa6-04-eureka-2-2-2-inicial.json](../evidence/historico/eft/etapa6-04-eureka-2-2-2-inicial.json)
- [etapa6-04-eureka-2-2-2.json](../evidence/eft/etapa6-04-eureka-2-2-2.json)
- [etapa6-05-routing-account-service.json](../evidence/eft/etapa6-05-routing-account-service.json)
- [etapa6-05-routing-customer-service.json](../evidence/eft/etapa6-05-routing-customer-service.json)
- [etapa6-05-routing-payment-service.json](../evidence/eft/etapa6-05-routing-payment-service.json)
- [etapa6-06-flujo-financiero-escalado.json](../evidence/eft/etapa6-06-flujo-financiero-escalado.json)
- [etapa6-07-idempotencia.json](../evidence/eft/etapa6-07-idempotencia.json)
- [etapa6-07b-oauth-401-403.json](../evidence/eft/etapa6-07b-oauth-401-403.json)
- [etapa6-08-outbox-coordinada.json](../evidence/eft/etapa6-08-outbox-coordinada.json)
- [etapa6-08b-routing-interservicios-loadbalancer.json](../evidence/eft/etapa6-08b-routing-interservicios-loadbalancer.json)
- [etapa6-09-consumer-group-payment.json](../evidence/eft/etapa6-09-consumer-group-payment.json)
- [etapa6-10-failover-account-service.json](../evidence/eft/etapa6-10-failover-account-service.json)
- [etapa6-10-failover-customer-service.json](../evidence/eft/etapa6-10-failover-customer-service.json)
- [etapa6-10-failover-payment-service.json](../evidence/eft/etapa6-10-failover-payment-service.json)
- [etapa6-10a-diagnostico-cache-503.json](../evidence/eft/etapa6-10a-diagnostico-cache-503.json)
- [etapa6-10a-pending-tras-cache-503.json](../evidence/eft/etapa6-10a-pending-tras-cache-503.json)
- [etapa6-10b-recuperacion-replicas.json](../evidence/eft/etapa6-10b-recuperacion-replicas.json)
- [etapa6-10c-pending-recuperado-sin-duplicacion.json](../evidence/eft/etapa6-10c-pending-recuperado-sin-duplicacion.json)
- [etapa6-11-postgresql-final.json](../evidence/eft/etapa6-11-postgresql-final.json)
- [etapa6-12-compose-final.json](../evidence/eft/etapa6-12-compose-final.json)
- [etapa6-13-tests-focalizados.txt](../evidence/eft/etapa6-13-tests-focalizados.txt)
- [etapa6-13b-tests-focalizados-retry.txt](../evidence/eft/etapa6-13b-tests-focalizados-retry.txt)
- [etapa6-14-mvn-verify-antes-escala.txt](../evidence/historico/eft/etapa6-14-mvn-verify-antes-escala.txt)
- [etapa6-14b-mvn-verify-antes-retest.txt](../evidence/historico/eft/etapa6-14b-mvn-verify-antes-retest.txt)
- [etapa6-15-mvn-verify-final.txt](../evidence/eft/etapa6-15-mvn-verify-final.txt)
- [etapa6-16-bff-legacy-regresion-final.json](../evidence/eft/etapa6-16-bff-legacy-regresion-final.json)
- [etapa6-17-account-host-8085-base.json](../evidence/eft/etapa6-17-account-host-8085-base.json)
- [etapa6-18-build-imagenes.json](../evidence/eft/etapa6-18-build-imagenes.json)
- [etapa6-19-postgresql-verificacion-final-tests.json](../evidence/eft/etapa6-19-postgresql-verificacion-final-tests.json)

## 13. Git / límites / nube

Commits locales: 8d30d80 auditoría; c527100 outbox; 6a955c8 discovery/TLS; 2dcdd9c retry/cache; 6d28e5c runner/evidencias. Informe, plan y procedimiento local se registran en el commit de documentación de cierre.

Sin pendientes bloqueantes de Etapa 6. Límites:

- Dos réplicas demostradas por negocio, sin prueba de capacidad/SLA.
- Failover runtime con parada individual controlada; claim abandonado cubierto en PostgreSQL de tests.
- PostgreSQL/Kafka/Auth/Config/Eureka singleton por alcance; no se ofrece HA de infraestructura.
- TLS/keystore locales compartidos; nube requiere PKI/secretos gestionados y issuer/discovery/ingress apropiados.
- Base fresca en nube requiere migración coordinada antes de múltiples réplicas; no extrapolar el inicializador local a pipeline productivo.
- Mantener ledger idempotente y deduplicación ante redelivery; no prometer exactly-once físico.
- BFF siguen legacy; integración moderna/titularidad necesitan alcance propio.

Siguiente etapa recomendada: preparar infraestructura cloud real, observabilidad, PKI/secretos y pipeline de migración sobre estos servicios escalables; desplegar con los controles operativos correspondientes. No se creó evidencia ficticia AWS ni se desplegó en esta etapa.
