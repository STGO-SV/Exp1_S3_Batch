# Índice de evidencias finales

**20 evidencias principales** seleccionadas. Fechas/alcances distintos; no presentar todo como captura nueva o cloud. JSON/TXT EFT contienen resultados reproducibles, no capturas gráficas. Ver timestamps; no incluir secretos/logs completos.

## A. Evidencias principales

| # | Evidencia | Archivo | Qué demuestra / límites | Criterios |
|---|---|---|---|---|
| 1 | Validación vigente | [etapa7-1-01-mvn-verify.txt](../evidence/eft/etapa7-1-01-mvn-verify.txt) | 256 reportadas, seis PostgreSQL reales, BUILD SUCCESS; caveat KafkaReal. | C3/C7 |
| 2 | Tres jobs Batch | [semana-8.evidencia-batch-3-jobs-status-COMPLETED.png](../evidence/semana-8/semana-8.evidencia-batch-3-jobs-status-COMPLETED.png) | Captura histórica Semana 8; complementar restart/paralelismo con tests. | C1/C3 |
| 3 | Tres BFF vigentes | [etapa6-16-bff-legacy-regresion-final.json](../evidence/eft/etapa6-16-bff-legacy-regresion-final.json) | Web/Mobile/ATM 200 y mismas respuestas completas legacy tras escala. | C4 |
| 4 | TLS BFF | [etapa5-02-tls-vigente-https-health.json](../evidence/eft/etapa5-02-tls-vigente-https-health.json) | Certificado/SAN/validez/hostname/health de los tres. | C4 |
| 5 | OAuth canales | [etapa5-04-oauth-401-403.json](../evidence/eft/etapa5-04-oauth-401-403.json) | Sin token 401, otro rol/canal 403; no control SCOPE adicional BFF. | C4 |
| 6 | Customer | [05-customer.json](../evidence/eft/05-customer.json) | Alta/consulta/actualización y PostgreSQL real. | C5 |
| 7 | Account | [06-account-apertura.json](../evidence/eft/06-account-apertura.json) | Apertura, consulta y mantenimiento. | C5 |
| 8 | Payment escalado | [etapa6-06-flujo-financiero-escalado.json](../evidence/eft/etapa6-06-flujo-financiero-escalado.json) | Depósito/transferencia/pago y concurrencia de fixtures. | C5/C6 |
| 9 | Idempotencia | [etapa6-07-idempotencia.json](../evidence/eft/etapa6-07-idempotencia.json) | Replay en otra réplica y conflicto sin otro posting. | C2/C5 |
| 10 | Cierre/rollback | [10-cierre-rollback.json](../evidence/eft/10-cierre-rollback.json) | CLOSED conserva saldo y rechazos no mueven fondos. | C5 |
| 11 | Resilience4j caída | [etapa5-05-mobile-resilience-503.json](../evidence/eft/etapa5-05-mobile-resilience-503.json) | Mobile 200 antes; Account detenido; 503 controlado. | C4/C5 |
| 12 | Resilience4j recuperación | [etapa5-06-mobile-recuperacion-200.json](../evidence/eft/etapa5-06-mobile-recuperacion-200.json) | Misma URL recuperada 200 tras restaurar Account. | C4/C5 |
| 13 | Kafka/outbox/audit | [13-kafka-outbox-audit.json](../evidence/eft/13-kafka-outbox-audit.json) | Publicación, consumo real, lag y deduplicación. | C5 |
| 14 | Kafka DLT | [14-kafka-dlt.json](../evidence/eft/14-kafka-dlt.json) | JSON inválido controlado en DLT, sin caída ficticia de broker. | C5 |
| 15 | Reconciliación | [12b-kafka-reconciliacion-pending.json](../evidence/eft/12b-kafka-reconciliacion-pending.json) | Evento completa PENDING sin duplicar posting. | C2/C5 |
| 16 | Eureka 2+2+2 | [etapa6-04-eureka-2-2-2.json](../evidence/eft/etapa6-04-eureka-2-2-2.json) | Seis UP con IDs/IPs distintos y puertos seguros. | C6 |
| 17 | Routing efectivo | [etapa6-08b-routing-interservicios-loadbalancer.json](../evidence/eft/etapa6-08b-routing-interservicios-loadbalancer.json) | Ambos Account atendieron llamadas Payment, sin alterar payload. | C5/C6 |
| 18 | Failover Account | [etapa6-10-failover-account-service.json](../evidence/eft/etapa6-10-failover-account-service.json) | GET/operación con una réplica detenida; operación recuperada. | C6 |
| 19 | Preservación PostgreSQL | [etapa6-11-postgresql-final.json](../evidence/eft/etapa6-11-postgresql-final.json) | Filas previas de once tablas y 25 operaciones del run. | C2/C6 |
| 20 | Estado final healthy | [etapa6-12-compose-final.json](../evidence/eft/etapa6-12-compose-final.json) | 1+1+1, 13 servicios, infraestructura/volumen y lag final. | C6/C7 |

## B. Evidencias complementarias

- Batch: [validacion.txt](validacion.txt), [auditoría](auditoria-inicial.md), BatchJobsRestartIntegrationTests, Week3PartitionedJobsIntegrationTests y PartitionReaderRestartTests. H2 aislado no es restart runtime PostgreSQL multiworker.
- [Outbox coordinada](../evidence/eft/etapa6-08-outbox-coordinada.json), [grupo Kafka](../evidence/eft/etapa6-09-consumer-group-payment.json) y FinancialOutboxPostgresTests: dos owners, claims/lease/stale token, PostgreSQL real.
- [Failover Customer](../evidence/eft/etapa6-10-failover-customer-service.json), [Payment](../evidence/eft/etapa6-10-failover-payment-service.json) y [recuperación única](../evidence/eft/etapa6-10c-pending-recuperado-sin-duplicacion.json).
- [Web](../evidence/eft/etapa5-03-web-200.json), [Mobile](../evidence/eft/etapa5-03-mobile-200.json), [ATM](../evidence/eft/etapa5-03-atm-200.json).
- [Scopes modernos](../evidence/eft/etapa6-07b-oauth-401-403.json), [detalle PostgreSQL](../evidence/eft/22-postgresql-detalle.json), [Account host 8085](../evidence/eft/etapa6-17-account-host-8085-base.json).
- Restantes EFT: builds/config/versiones, continuidad DLT, verifies anteriores y tests focalizados. Para preguntas del evaluador.

## C. Históricas / debug

Diagnóstico TLS anterior Etapa 5 y BFF fuera de alcance Etapa 4: causas corregidas. etapa6-10a-diagnostico-cache-503.json y etapa6-10a-pending-tras-cache-503.json: desafío real, mostrar siempre junto a recuperación. Variantes *-inicial.json: trazabilidad del primer escenario, no estado final.

Semanas 5–8/benchmarks: antecedentes y datasets acotados, no nuevo runtime EFT. Etapas 1–2 y README-semana-8.md: evolución; pendientes antiguos pueden estar resueltos.

Inventario exhaustivo: [auditoría final](auditoria-documental-final.md). Las 20 principales son un banco de selección: extraer resultados legibles con referencia, no insertar archivos completos en informe/video.
