> Nota documental Etapa 7.1 (2026-10-06): este diagnóstico conserva el estado inicial. La clasificación funcional siguiente no responde a C1; la lista oficial y los puntajes están en [matriz vigente](matriz-rubrica-evidencias.md). Pauta/instrucciones disponibles; laboratorio cloud pendiente de ejecución y plantilla PDF pendiente de localizar.

# EFT — auditoría técnica inicial

Fecha: 4 de octubre de 2026. Proyecto: banco-legacy-batch.
Base verificada: main limpio, 76b977346bd4b84c85aa70f34e7ff7a8bb8e4ede.
Se ejecutaron git status y git log antes de modificar; git fetch origin main confirmó la misma base.
Rama de trabajo: eft. Sin push, merge ni despliegue cloud.

## Alcance y evidencia

Auditoría del código, configuración, esquema SQL, tests, documentación/evidencia de semanas anteriores y encabezados de los nueve CSV de bank_legacy_data/data/semana_1..3.
No se inspeccionaron filas de una base productiva ni se atribuye a esta ejecución la evidencia histórica de Semana 8.
La matriz inicial se elaboró usando la transcripción de requisitos; queda como diagnóstico histórico, sustituido para evaluación por la matriz vigente alineada con la pauta oficial.
AWS/despliegue real y plantilla PDF son dos consultas independientes pendientes. Ninguna bloquea esta auditoría ni el scaffolding. No se creó PDF ni video.

## Cinco procesos funcionales y dominios modernizados — clasificación histórica

1. Procesamiento de transacciones diarias: validar datos y detectar anomalías con trazabilidad; evitar pérdida al publicar eventos.
2. Cálculo mensual de intereses: aplicar reglas existentes sobre cuentas y saldos; reconciliar aceptados/rechazados y recuperar fallos.
3. Consolidación anual de movimientos: generar información de estados de cuenta; conservar exactitud y reanudación. El job actual persiste movimientos, no produce un documento anual completo.
4. Operación de cuentas y pagos por canal: mantener consultas Web/Mobile/ATM, evolucionar apertura/cierre/mantenimiento y pagos/transferencias/depósitos con atomicidad e idempotencia.
5. Gestión del cliente y su relación con cuentas: establecer identidad, titularidad y perfil para autorización por recurso y actualización de información personal.

Son una clasificación funcional de las capacidades existentes; no corresponden a los cinco procesos de modernización oficiales de C1.

## Matriz EFT

| Criterio EFT | Requisito | Estado actual | Implementación existente | Brecha | Acción necesaria | Riesgo |
|---|---|---|---|---|---|---|
| Procesos críticos | Identificar cinco | Propuesta documentada | Tres jobs, consultas/retiro, datos de nombre/edad | Validación contra enunciado completo | Confirmar priorización en arquitectura | Bajo |
| Arquitectura | Justificar requisitos de negocio | Base útil, parcial | BFF → Account; Batch → PostgreSQL/outbox → Kafka → Anomaly; Auth/Config/Eureka | ATM escribe BD; faltan dominios Account/Payment/Customer completos | Delimitar ownership y contratos antes de migrar | Alto |
| Batch | Tres procesos | Implementado | BatchJobsConfig y tres pares reader/processor/writer | Estados anuales no generan informe final | Confirmar entregable del proceso anual | Medio |
| Batch | Errores avanzados | Implementado con límites | Retry transitorio 3 intentos; skip parse/dominio; rollback por chunk; métricas/listeners | Fallos en prelectura no llegan al skip; límite por worker; sin backoff explícito | Mantener fail-fast; acordar tolerancia global y rechazo auditable | Medio |
| Batch | Reanudación | Verificado en tests EFT | JobRepository, ExecutionContext y reader ItemStream | Runner usa timestamp nuevo; no comando operativo de restart | Definir parámetros de negocio estables y procedimiento | Alto |
| Batch | Paralelismo/escalabilidad | Local implementado | Partitioner por rangos, StepScope y ThreadPoolTaskExecutor | Cada worker escanea todo el archivo; no remote partitioning | Benchmark representativo y diseño de entradas por partición | Medio/alto |
| Batch | Idempotencia | Parcial | Misma JobInstance completa no vuelve a ejecutarse | Nuevos parámetros duplican salidas; no claves naturales/periodo/fingerprint | Definir identidad del lote y claves antes de migrar esquema | Alto |
| BFF | Web/Mobile/ATM independientes | Implementado con acoplamiento ATM | Tres módulos/procesos y contratos distintos | ATM tiene lógica transaccional y acceso a BD compartida | Conservar y migrar después bajo pruebas | Medio |
| BFF | Payloads optimizados | Implementado | Web dashboard; Mobile resumen/5 movimientos; ATM saldo/3 movimientos | Métricas históricas no prueban carga EFT actual | Repetir evidencia cuando el dominio esté completo | Bajo |
| BFF | OAuth2/roles/HTTPS/token relay | Implementado | Resource Server RSA/JWT; roles por canal; HTTPS; Authorization reenviado | No autorización por titularidad; scopes no controlan endpoints directamente | Definir identidad de cliente y autorización por cuenta | Alto |
| BFF | Resilience4j/fallback | Implementado | accountService CircuitBreaker; 404 conservado; fallback 503 | Retiro local no pasa por resiliencia remota | Aplicar circuit breaker al futuro contrato Payment | Medio |
| Negocio | Gestión de Cuentas: apertura/cierre/mantenimiento | Incompleto | Account solo GET sobre resultados Batch; datasource read-only | Sin entidad maestra ni ciclo de vida | Diseñar estado/identidad/reglas y migraciones aprobadas | Alto |
| Negocio | Pagos/transferencias/depósitos | Scaffolding preparado | Retiro ATM reutilizable; importación histórica de tipos | No ejecución de esas operaciones ni ledger/contrapartes | Definir atomicidad, idempotency-key, monedas y estados | Alto |
| Negocio | Gestión de Clientes | Scaffolding preparado | nombre/edad en intereses.csv; nombre persiste | Sin customer_id, contacto, perfil ni vínculo fiable | Obtener definición/fuente de identidad y titularidad | Alto |
| Cloud | Config/Eureka/balanceo | Existente | Config native y clientes BFF/Account; Eureka; RestClient LoadBalanced | No todos los módulos usan Config/Eureka; endpoints de réplicas comparten DNS | Evidenciar registro y tráfico por instancia; plan HA | Medio |
| Seguridad | Spring Security/OAuth2 | Conservado | Auth client_credentials con roles WEB/MOBILE/ATM; claims/issuer/audience/firma/tiempo | Clientes in-memory; tokens identifican canal, no titular | Definir seguridad del dominio; no simular usuarios | Alto |
| Resilience4j | Tres servicios de negocio | Pendiente | Uso real en tres BFF, no en Account/Payment/Customer | Los tres dominios aún no tienen llamadas remotas reales | Aplicar cuando haya dependencia de negocio justificada | Medio |
| Kafka | Productores/consumidores reales | Implementado en Batch/Anomaly | Outbox transaccional, producer idempotente, consumer dedup, retry/DLT | No prueba todavía integración de los tres dominios exigidos | Eventos posteriores al commit y consumidores con propósito | Medio |
| Docker | Compose | Conservado y extendido opcionalmente | Base 11 servicios; Dockerfile MODULE; perfil EFT añade dos | Nuevas imágenes y arranque del perfil no comprobados aquí | Build y smoke test local tras revisión | Bajo/medio |
| Escalado | ≥3 microservicios horizontales | Preparado, no demostrado | Override puertos efímeros e IDs únicos | Falta prueba runtime y reparto por instancia; shells nuevos sin negocio | Demostrar réplicas, salud, registry y carga | Medio |
| Cloud | Preparación/despliegue | Diseño pendiente; sin desplegar | Contenedores, variables, health y servicios independientes | Persistencia/HA/TLS/secretos/observabilidad cloud | Preparar alternativas; esperar respuesta para despliegue real | Medio |
| Entrega | PDF/video/plantilla | Diferido expresamente | Documentos de trabajo Markdown | Plantilla ausente y aclaración docente | Esperar respuesta; no inventar formato | Bajo |

## Batch: revisión individual

| Job | Reader | Processor | Writer | Partición |
|---|---|---|---|---|
| transaccionesDiariasJob | transaccionReader: FlatFileItemReader + PartitionRangeItemReader, transacciones.csv | TransaccionProcessor: valida presencia de ID/fecha, monto y tipo y marca anomalía | CompositeItemWriter: transaccionJdbcWriter + AnomalyOutboxItemWriter | rango de id |
| interesesMensualesJob | interesReader: mismos wrappers, intereses.csv | InteresProcessor: campos requeridos, saldo no negativo, ahorro/préstamo y tasa | JdbcBatchItemWriter → interes_procesado | rango de cuenta_id |
| estadosCuentaAnualesJob | movimientoAnualReader: mismos wrappers, cuentas_anuales.csv | MovimientoAnualProcessor: validación del movimiento | JdbcBatchItemWriter → movimiento_anual_procesado | rango de cuenta_id |

Los tres workers usan chunk transaccional (100 en configuración principal), skip de FlatFileParseException/InvalidBatchDataException hasta 1000 por worker y retry de TransientDataAccessException con 3 intentos totales. Excepciones no clasificadas, agotamiento del retry o exceso del skip provocan FAILED. Los chunks previos permanecen confirmados: no hay rollback global del archivo ni de los tres jobs juntos. BatchJobRunner detiene la cadena si un job no termina COMPLETED; no aborta necesariamente al instante otros workers que ya estén corriendo.

Los tres managers usan partitioner + worker + gridSize + partitionTaskExecutor. El executor usa pool fijo, cola gridSize-threadCount y espera al cierre. Cada reader es StepScope y delega open/update/close al FlatFileItemReader, que guarda posición en ExecutionContext. Se añadieron validación de valores positivos y pruebas de checkpoint.

### Reanudación y duplicación

BatchJobsRestartIntegrationTests ejecuta los tres jobs de producción con H2 aislado, grid/thread=1 y chunk=1. Inyecta un fallo crítico después de la segunda escritura JDBC: comprueba FAILED, rollback de esa escritura, conservación del primer chunk, restart de la misma JobInstance y salida final esperada (8/7/8). Para transacciones comprueba también unicidad de IDs y outbox esperado; la tercera ejecución con los mismos parámetros completos se rechaza. Es evidencia de restart de los tres jobs, no de restart multiworker ni de PostgreSQL real.

PartitionReaderRestartTests verifica que filas filtradas fuera de rango también quedan consideradas al guardar el checkpoint. Reiniciar requiere el mismo archivo sin modificaciones, mismos parámetros, esquema Batch persistente y configuración de particiones estable.

BatchJobRunner añade ejecucion=System.currentTimeMillis(), creando una nueva instancia en cada arranque habilitado. Los writers hacen INSERT sin clave natural única en sus tablas. La unicidad UUID del outbox/consumidor protege contra redelivery del mismo evento, no contra regenerar eventos al reprocesar el lote. No se cambió esto: intereses carece de periodo y movimientos anuales de ID de origen; deduplicar por campos puede eliminar movimientos legítimos.

### Límites ante grandes volúmenes

CsvColumnRangePartitioner prelee el archivo completo para hallar min/max; cada uno de P workers vuelve a escanear N filas: aproximadamente O(P*N) lecturas, aunque procesa solo su rango. Rangos numéricos pueden distribuir mal IDs dispersos o cuentas con muchos movimientos. El prelector usa split por coma y parseLong: cabeceras faltantes, IDs malformados, archivos vacíos o solo cabecera fallan antes del skip. Los saltos de parsing sin ID no se pueden asignar limpiamente a un worker. El límite de skip es por worker, no global. La aritmética de rangos long extremos no está endurecida. No se realizó un rediseño unilateral.

No hay remote partitioning ni coordinación multiinstancia del publisher outbox. No escalar batch con --scale sin diseñar identidad del lote y leasing de publicaciones. Configuración initialize-schema=always también requiere revisión para arranques concurrentes sobre PostgreSQL.

### Tests existentes relevantes

BatchJobsIntegrationTests, Week3PartitionedJobsIntegrationTests (cuatro workers y reconciliación), PartitioningUnitTests, PartitionBenchmarkTests, ProcessorsTests, RetryPolicyIntegrationTests (step de prueba, no fallo inyectado en cada worker real), AnomalyOutboxTransactionTests, writer/event/listener/outbox tests y KafkaRealOutboxIntegrationTests. Se añadieron los tres archivos de pruebas EFT mencionados. Los benchmarks del dataset de 1000 filas son evidencia acotada, no garantía para millones de registros.

## BFF y seguridad por canal

| Canal | Contrato/optimización | Seguridad y comunicación | Resiliencia/tests | Conclusión |
|---|---|---|---|---|
| Web | GET dashboard: titular, saldos, tasa, detalle de 20 movimientos y 10 anomalías | ROLE_WEB, JWT, HTTPS; relay hacia /internal/accounts/*/web-dashboard | RemoteAccountServiceClientTests y WebJwtSecurityTests; contexto | Fuerte cumplimiento técnico del patrón; anomalías son globales, no vinculadas a la cuenta |
| Mobile | GET summary y movements: saldo/tipo y 5 movimientos sin descripción | ROLE_MOBILE; JWT/HTTPS/relay; RestClient LoadBalanced | contexto/JWT/cliente, MobileRemoteIntegrationTests y MobileConfigClientIntegrationTests | Contrato reducido y configuración externa verificables |
| ATM | GET balance/movements (tipo/monto, 3); POST withdrawal | ROLE_ATM; JWT/HTTPS/relay solo en consultas; retiro escribe PostgreSQL localmente | contexto/JWT/cliente, AtmDataIntegrationTests y AtmWithdrawalServiceTests | Canal diferenciado y seguro por rol; independencia funcional limitada por lógica/BD del retiro |

Cada BFF dispone de cliente Config, Eureka, Spring Cloud LoadBalancer, connect/read timeouts 2s/3s y circuit breaker accountService. Fallback devuelve error 503 por indisponibilidad; no inventa saldos ni ejecuta pagos ficticios. Tests de clientes comprueban relay, 404 y apertura de circuito.

Auth entrega scopes por client_credentials y roles por canal. Las reglas HTTP usan ROLE_*; no validan SCOPE_* como autorización adicional. El decoder comprueba RSA ≥2048, firma, issuer, audience, exp/iat/sub y formato de roles; no mapea cuentas a usuarios. Esto es una brecha de dominio, no algo que pueda resolverse inventando customer_id. HTTPS interno BFF/Account y Auth existe; Config/Eureka/Kafka/PostgreSQL usan red local sin TLS equivalente. Healthchecks curl --insecure comprueban disponibilidad, no confianza del certificado.

Conclusión: los tres BFF satisfacen razonablemente separación técnica, payloads, OAuth2, HTTPS y fallback del criterio transcrito. No certificar nivel máximo sin la pauta completa: ATM mantiene negocio/BD, falta titularidad y no se ha repetido evidencia end-to-end en esta etapa. No se reescribieron.

## Modelo real y tres microservicios de negocio

### Account

InternalAccountController solo expone consultas GET; LegacyAccountReadRepository lee la última fila de interes_procesado como saldo vigente, movimientos y anomalías. Hikari está read-only en su configuración. No existen comandos de apertura, cierre, mantenimiento, estado de cuenta activa/cerrada, cuenta maestra ni titularidad. No cumple por sí solo Gestión de Cuentas completa.

### Payment

No existe procesador online de pago/transferencia/depósito. Los tipos de transacción en CSV/modelos y sus filas procesadas son historia/importación, no ejecución de una orden de pago. AtmWithdrawalService + LegacyAccountWithdrawalRepository sí proporcionan patrón reutilizable: validación decimal, SELECT FOR UPDATE sobre último saldo, actualización + movimiento en una transacción, rechazo por saldo insuficiente y tests. No ofrece idempotency-key ni separación del saldo Batch/operativo.

Nuevo banco-legacy-payment-service: módulo Maven, aplicación Boot, Config/Eureka, OAuth2 Resource Server, health, HTTPS configurado, pruebas contexto/seguridad y Dockerfile compatible. Sin dependencias JDBC/core ni rutas ficticias. No se trasladó el retiro; requiere decidir ownership del saldo, operaciones, contrapartes, estados y mecanismo de idempotencia.

### Customer

Encabezados reales:
- transacciones.csv: id,fecha,monto,tipo.
- intereses.csv: cuenta_id,nombre,saldo,edad,tipo.
- cuentas_anuales.csv: cuenta_id,fecha,transaccion,monto,descripcion.

CuentaInteres lleva nombre y edad; InteresProcesado/esquema conservan nombre pero descartan edad. No existe tabla cliente/titular/perfil, documento, contacto, customer_id ni relación cliente-cuenta. nombre no es una identidad única; cuenta_id es una cuenta, no una persona. Transacciones ni siquiera contienen cuenta_id, por lo que no corresponde asignar sus anomalías a un cliente arbitrario.

Nuevo banco-legacy-customer-service con la misma infraestructura base y pruebas. Sin modelo persistente ni endpoints de perfiles. Pendiente definir fuente confiable, identidad, datos mínimos, vínculo con cuentas y reglas de actualización/privacidad.

Los módulos nuevos exponen únicamente health sin autenticación; demás rutas denegadas por defecto, incluso con JWT válido. Se reutilizó el decoder y política stateless del Account; errores OAuth2 conservan 401/403. La estructura code/message para errores de dominio se adoptará al existir operaciones reales; no se añadieron handlers sin uso.

## Kafka, outbox y Resilience4j

Actual: Batch guarda transacción + evento outbox atómicamente; publisher obtiene PENDING, envía con retries/backoff y marca publicado. Producer usa acks=all e idempotence. Una caída entre envío y confirmación DB puede reenviar: entrega al menos una vez. Consumer Anomaly deduplica event_id en PostgreSQL, deserializa con ErrorHandlingDeserializer y usa retry (dos reintentos tras entrega inicial) y DLT; errores de deserialización van directamente a recuperación. Anomaly incluye tests con broker embebido; no equivalen al Kafka de Compose. Cuatro tests KafkaReal* retornan sin ejecutar integración cuando no se activa -Dweek7.kafka.integration=true; el verify de esta etapa no activó esa opción.

findPending no hace claim/lease ni SELECT FOR UPDATE SKIP LOCKED. Múltiples publishers pueden publicar el mismo evento y competir al marcar estado; no escalar ese componente todavía. Consumers pueden compartir group-id; paralelismo efectivo limitado por particiones. El script de laboratorio Semana 7 crea tres particiones; el Compose principal no declara provisioning de topics: no asumir que el broker actual tiene tres.

Propuesta, pendiente de contratos:
- Account publica AccountOpened/AccountClosed después del commit; Customer puede actualizar una proyección de vínculos cuando la fuente/relación esté definida.
- Payment publica PaymentCompleted/TransferCompleted después de operación confirmada mediante outbox; Account consume con dedup para una proyección de movimientos si se decide que esa proyección es eventual. No repartir el débito/crédito atómico entre consumidores sin saga/ledger acordado.
- Customer publica CustomerProfileUpdated; Account consume para una proyección de presentación cuando no se requiera consultar datos personales en cada request.
- Payment consulta elegibilidad/estado de cuenta en Account con timeout/circuit breaker y fallback de rechazo temporal; jamás confirmar un pago durante fallback. Una consulta de perfil en Customer puede degradar solo campos opcionales, nunca autorización.

No se añadieron Kafka ni Resilience4j sin consumidores/llamadas con propósito. Hoy esos requisitos no están demostrados en los tres servicios de negocio. Anomaly/outbox/retry/DLT se conservaron.

## Docker y escalabilidad

| Servicio | Estado externo/local | Capacidad actual | Impedimento/condición |
|---|---|---|---|
| Account | Stateless HTTP, PostgreSQL compartido | Lecturas replicables | Host 8085 fijo; IDs Eureka/endpoint y pool DB |
| Web/Mobile | Stateless HTTP | Replicables | Host 8081/8082 fijo; acceso a instancias y métricas |
| ATM | Stateless HTTP; saldo en DB con bloqueo | Replicable con precaución | Host 8083 fijo; solicitudes repetidas no idempotentes |
| Payment/Customer | Scaffolding stateless | Replican health, no negocio | Sin contratos; prueba de contenedores pendiente |
| Anomaly | Estado persistido DB + offsets Kafka | Consumer group replicable | Particiones; etiqueta ANOMALY_CONSUMER_INSTANCE fija reduce trazabilidad |
| Batch/outbox | Repository/chunks/outbox compartidos | Paralelismo local | Runner timestamp; publisher sin leasing; no recomendar réplicas |
| Auth | Clientes/estado de autorizaciones en memoria | No asumir HA | Host 8084; persistencia de autorizaciones y secretos comunes |
| Config | Native repo montado | Potencialmente replicable | Host 8888 fijo; clientes usan endpoint único |
| Eureka | Registro en memoria | Singleton local | Host 8761; necesita peers para HA |
| PostgreSQL/Kafka | Stateful | No usar --scale como HA | Volumen DB, broker/node-id fijos, quorum/replicación no diseñados |

No hay container_name en el Compose principal. Dependencias usan postgres/config-server/discovery-server/kafka y nombres internos correctos; BFF descubre nombre lógico banco-legacy-account-service. Auth issuer https://localhost:8084 es identidad del emisor; no se cambió a nombre Docker arbitrario.

Cambios:
- docker-compose.yaml intacto.
- docker/compose.eft.yaml agrega perfil eft con Payment/Customer, health HTTPS, secretos por placeholders y sin puertos de host.
- docker/compose.scale-local.yaml reemplaza puertos fijos de Account y los tres BFF por puertos de host efímeros; IDs Eureka únicos mediante placeholder Spring escapado para Compose.
- docker/Dockerfile incorpora POM/src de ambos módulos al contexto del reactor.

Validación sintáctica realizada con Docker Compose v5.1.4 mediante config --quiet, sin imprimir secretos. !override requiere Compose ≥2.24.4. No se construyeron ni arrancaron las nuevas imágenes, ni se ejecutó --scale.

Preparación local (ejecutar después de revisar; no es evidencia de ejecución):

    docker compose -f docker-compose.yaml -f docker/compose.eft.yaml -f docker/compose.scale-local.yaml --profile eft up -d --build --scale account-service=3 --scale payment-service=3 --scale customer-service=3
    docker compose -f docker-compose.yaml -f docker/compose.eft.yaml -f docker/compose.scale-local.yaml --profile eft ps
    docker compose -f docker-compose.yaml -f docker/compose.eft.yaml -f docker/compose.scale-local.yaml --profile eft port --index 1 account-service 8085

Alternativa con tres aplicaciones que ya tienen negocio: --scale web-bff=3 --scale mobile-bff=3 --scale atm-bff=3. El override cubre sus colisiones de puertos. Health de Payment/Customer no demuestra cumplimiento de tres dominios funcionales ni throughput.

Límites pendientes de evidencia:
- IDs únicos evitan reemplazo de registros; varias instancias siguen anunciando el mismo DNS de servicio. Docker DNS puede resolver réplicas, pero eso no prueba reparto individual de Spring Cloud LoadBalancer ni failover por instancia.
- prefer-ip-address para endpoints únicos exige certificados que cubran IP/DNS de cada réplica. No activar por defecto con el certificado actual.
- Certificado del inicializador incluye localhost/auth/account/web/mobile/atm/127.0.0.1; no payment/customer. Healthchecks internos localhost funcionan con --insecure; futuras llamadas HTTPS a los nuevos nombres requieren extender SAN y renovar controladamente, conservando idempotencia y sin rotar secretos innecesariamente. No se alteró el inicializador.
- Gateway/proxy adicional se justifica cuando se requiera un endpoint estable hacia BFF replicados; no se añadió sin prueba de necesidad.
- Demostración completa: nueve contenedores saludables, tres IDs por servicio en Eureka, peticiones por instancia, carga/failover y saldos consistentes. No basta un ps.

## Preparación cloud sin despliegue

Propuesta: contenedores stateless en servicio administrado u orquestador; PostgreSQL/Kafka externos persistentes; Config versionada con secretos externos; TLS confiable; discovery/load balancing compatible; readiness/liveness separados; logs/métricas/correlación; límites de conexiones y CPU; migraciones versionadas y CI. Auth requiere persistencia de autorizaciones para HA. Elegir proveedor/costos/recursos o ejecutar deploy depende de la aclaración docente y decisiones posteriores. No se creó infraestructura cloud ni se expusieron secretos.

## Validación y pendientes

Resultados exactos del reactor y lista completa de archivos: informe-etapa-1.md y validacion.txt.
Pruebas locales de H2/broker embebido no sustituyen PostgreSQL real, arranque Docker ni certificados/concurrencia entre contenedores.
Decisiones detenidas: esquema maestro de cuenta, identidad de cliente/titularidad, ledger/atomicidad/idempotencia de pagos, claves naturales/periodos de lotes, estrategia TLS/balanceo por réplica, AWS y plantilla PDF.