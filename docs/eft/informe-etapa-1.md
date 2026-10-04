# EFT — informe de cierre de primera etapa

## Estado de rama

- Rama: eft.
- Base: 76b9773; main y origin/main permanecen en esa base, confirmada después de fetch.
- Commit local 900a8d6: valida paralelismo Batch y verifica rollback/restart de los tres jobs.
- Commit local bcd13d8: prepara Payment/Customer seguros y Compose opcional.
- Este informe, auditoría, plan y validación se guardan en un tercer commit local documental; su identificador se informa en la respuesta de cierre.
- Estado esperado después del commit documental: árbol limpio; verificado al cierre.
- Sin push ni merge a main; sin despliegue cloud.

## Batch

Ya existían tres jobs con reader CSV StepScope, procesadores de dominio, writers JDBC, chunks transaccionales, skip, retry transitorio y particiones con ThreadPoolTaskExecutor. Los tests existentes demuestran reconciliación y ejecución de cuatro workers sobre Semana 3.

Mejoras: validación fail-fast de thread-count/grid-size positivos; cuatro casos de configuración inválida; checkpoint del reader con filas filtradas; tres casos de fallo crítico después de escritura JDBC y restart de los jobs reales. Se verifican rollback del chunk, conservación de lo confirmado, misma JobInstance, totales 8/7/8 y bloqueo de otra ejecución completa con los mismos parámetros.

Pendiente: identidad estable por periodo/lote, idempotencia entre instancias nuevas, procedimiento operativo de restart, prueba multiworker/PostgreSQL real, política global de rechazos, escalado de lectura CSV (cada partición relee todo), publisher outbox con lease antes de replicarlo. No se cambió el esquema ni las reglas financieras.

## BFF

- Web: dashboard con datos detallados, límites de movimientos/anomalías, ROLE_WEB, relay JWT y fallback 503; anomalías globales sin vínculo con cuenta.
- Mobile: resumen y movimientos compactos, ROLE_MOBILE, relay JWT; configuración central y pruebas del cliente.
- ATM: saldo/movimientos esenciales, ROLE_ATM y retiro transaccional probado. El retiro sigue acoplado a la base compartida.
- Los tres conservan HTTPS, Resource Server, Config/Eureka, LoadBalancer, timeouts y Resilience4j.
- Conclusión: fuerte cumplimiento técnico del criterio transcrito; no asegurar calificación máxima por ausencia de pauta completa, titularidad por recurso, acoplamiento ATM y falta de evidencia nueva de escala. Sin reescritura.

## Microservicios

Account sigue siendo servicio de consultas; no implementa apertura/cierre/mantenimiento. Su configuración de datasource es read-only.

Payment y Customer se crearon como módulos base del reactor Boot 3.3.4/Java 21. Incluyen aplicación, Config/Eureka, JWT compatible, configuración HTTPS, health, pruebas contexto/seguridad, Dockerfile y Compose opcional. Solo health está abierto; no hay operaciones ficticias ni tablas inventadas.

Payment puede reutilizar validación/bloqueo/rollback del retiro ATM, pero requiere definir ledger/saldo, contrapartes, estados e idempotency-key antes de implementar pagos/transferencias/depósitos.

Customer encuentra nombre/edad en intereses.csv, pero no customer_id ni titularidad/contactos/perfiles. El esquema persistido ni siquiera conserva edad. No convertir nombre o cuenta_id en identidad personal sin definición.

## Spring Cloud y seguridad

Config native y Eureka existentes se preservan; ambos módulos nuevos están preparados como clientes. BFF usa descubrimiento y RestClient LoadBalanced. No todos los módulos históricos usan Config/Eureka: Batch/Anomaly/Auth conservan sus configuraciones.

OAuth2 client_credentials por canal y validación RSA/firma/issuer/audience/claims se reutilizan. Las reglas HTTP autorizan por roles, no por scopes adicionales ni titularidad. Los clientes de Auth están en memoria; HA requiere decisiones posteriores.

Resilience4j es real en BFF → Account. En servicios de negocio faltan llamadas reales con propósito; propuesta documentada: Payment consulta elegibilidad en Account con fallback de rechazo temporal. No se agregaron circuit breakers vacíos.

## Kafka

Se conservaron Batch/outbox/Anomaly, producer idempotente, consumer dedup, retry y DLT.
Propuesta: AccountOpened/Closed, PaymentCompleted/TransferCompleted y CustomerProfileUpdated con outbox posterior al commit y consumidores justificados. Son contratos a definir, no implementaciones declaradas completas.
Outbox actual no tiene claim/lease para múltiples publishers; no escalarlo todavía.
Los tests embedded sí se ejecutaron. Los cuatro casos KafkaReal* no ejecutaron integración externa al faltar el flag explícito; esto no demuestra el Kafka de Compose.

## Docker y escalabilidad

Compose principal e inicializador idempotente de Semana 8 permanecen intactos.
Se incorporaron POM/src de módulos nuevos al Dockerfile parametrizado.
compose.eft.yaml añade servicios bajo perfil eft sin puertos publicados.
compose.scale-local.yaml permite puertos efímeros en Account/BFF e IDs de instancia únicos.

Validación Compose v5.1.4: config --quiet pasó.
No se ejecutaron builds de imágenes, arranque nuevo ni --scale. Las instrucciones locales están en auditoria-inicial.md.
La demostración de ≥3 servicios debe verificar contenedores/health/registry y reparto de tráfico; tres shells saludables no acreditan dominio completo.
Pendiente TLS por réplica/SAN de Payment/Customer y endpoints individuales: DNS compartido más UUID no prueba balanceo por instancia. No se añadió proxy complejo.

## Tests y evidencia

mvn verify: BUILD SUCCESS en todo el reactor; duración 2:11; 177 casos reportados, cero fallos/errores/skips. Cuatro casos de Kafka externo retornan anticipadamente; cobertura efectiva no debe confundirse con ese total.
Focalizadas Payment/Customer/Batch y restart: BUILD SUCCESS.
Distribución y comandos: validacion.txt.
git diff --check y Compose config --quiet: PASS.
Sin prueba nueva de PostgreSQL real, OAuth2 end-to-end Docker, confianza TLS entre réplicas ni cloud.

## Archivos modificados o creados

La siguiente lista es completa respecto de 76b9773 e incluye los documentos de esta etapa.

- banco-legacy-batch/src/main/java/com/duoc/banco_legacy_batch/config/PartitioningConfig.java
- banco-legacy-batch/src/test/java/com/duoc/banco_legacy_batch/BatchJobsRestartIntegrationTests.java
- banco-legacy-batch/src/test/java/com/duoc/banco_legacy_batch/PartitionExecutorConfigurationTests.java
- banco-legacy-batch/src/test/java/com/duoc/banco_legacy_batch/PartitionReaderRestartTests.java
- banco-legacy-customer-service/pom.xml
- banco-legacy-customer-service/src/main/java/com/duoc/banco_legacy/customer/CustomerServiceApplication.java
- banco-legacy-customer-service/src/main/java/com/duoc/banco_legacy/customer/security/CustomerSecurityConfig.java
- banco-legacy-customer-service/src/main/resources/application.properties
- banco-legacy-customer-service/src/test/java/com/duoc/banco_legacy/customer/CustomerServiceApplicationTests.java
- banco-legacy-customer-service/src/test/java/com/duoc/banco_legacy/customer/JwtTestSupport.java
- banco-legacy-customer-service/src/test/resources/application.properties
- banco-legacy-payment-service/pom.xml
- banco-legacy-payment-service/src/main/java/com/duoc/banco_legacy/payment/PaymentServiceApplication.java
- banco-legacy-payment-service/src/main/java/com/duoc/banco_legacy/payment/security/PaymentSecurityConfig.java
- banco-legacy-payment-service/src/main/resources/application.properties
- banco-legacy-payment-service/src/test/java/com/duoc/banco_legacy/payment/JwtTestSupport.java
- banco-legacy-payment-service/src/test/java/com/duoc/banco_legacy/payment/PaymentServiceApplicationTests.java
- banco-legacy-payment-service/src/test/resources/application.properties
- config-repository/banco-legacy-customer-service.yml
- config-repository/banco-legacy-payment-service.yml
- docker/compose.eft.yaml
- docker/compose.scale-local.yaml
- docker/Dockerfile
- docs/eft/auditoria-inicial.md
- docs/eft/informe-etapa-1.md
- docs/eft/plan-implementacion.md
- docs/eft/validacion.txt
- pom.xml

## Siguiente etapa recomendada, por prioridad

1. Resolver cuenta maestra/saldo operativo, identidad y titularidad de clientes, contratos de pagos e idempotencia.
2. Implementar apertura/cierre/mantenimiento en Account con migraciones acordadas.
3. Implementar Payment y migración controlada de retiro ATM, luego Customer usando fuentes reales.
4. Añadir eventos/consumidores y llamadas con Resilience4j que tengan propósito de dominio.
5. Robustecer identidad de lotes, lectura/restart multiworker y outbox antes de escalarlos.
6. Construir nuevas imágenes y demostrar localmente ≥3 microservicios funcionales replicados, TLS y failover.
7. Completar preparación cloud y entrega documental según respuestas docentes; sin despliegue hasta autorización.

Auditoría detallada: auditoria-inicial.md. Fases y criterios de salida: plan-implementacion.md.