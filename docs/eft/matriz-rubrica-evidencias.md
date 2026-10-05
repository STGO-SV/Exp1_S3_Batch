# Matriz de rúbrica → implementación → evidencia → documento

Fuente: requisitos transcritos por el usuario, incluidos los ocho criterios de Etapa 7. **No se recibió pauta oficial completa ni tabla de puntajes máximos.** No inventar números/total ni atribuir calificación máxima. La columna de requisito expresa la transcripción disponible, no una reproducción oficial de AVA.

COMPLETO: cobertura demostrada del criterio transcrito dentro del alcance académico; no aceptación docente ni calidad productiva. Evidencias sin prefijo corresponden a docs/evidence/eft; se enlazan/priorizan en el [índice](indice-evidencias-finales.md). Secciones § corresponden al borrador-informe-tecnico.md.

| Criterio | Puntaje máximo | Requisito disponible | Implementación | Evidencia / archivo concreto | Documento final | Estado |
|---|---|---|---|---|---|---|
| C1 — 5 procesos críticos | PENDIENTE DOCENTE: pauta no disponible | Cinco procesos: transacciones, intereses, movimientos anuales, cuentas/pagos y clientes/titularidad. | Tres jobs y tres dominios, BFF históricos conservados. | auditoria-inicial.md; modelo-dominio.md | §5 | COMPLETO |
| C2 — 3 requerimientos clave y justificación arquitectónica | PENDIENTE DOCENTE: pauta no disponible | Integridad/trazabilidad; seguridad por canal; disponibilidad/escalabilidad. Selección argumentada del proyecto, no cita AVA. | Ledger/idempotencia/outbox; OAuth/TLS/BFF; Config/Eureka/Resilience4j/escala. | etapa6-07-idempotencia.json; etapa5-04-oauth-401-403.json; etapa6-04-eureka-2-2-2.json | §6–7 | COMPLETO |
| C3 — 3 procesos Spring Batch con errores, paralelismo y escalabilidad | PENDIENTE DOCENTE: pauta no disponible | Tres jobs con retry/skip/rollback/restart y partitioning/TaskExecutor; escalabilidad local con límites. | BatchJobsConfig/PartitioningConfig, readers ItemStream, tres restart tests y cuatro workers. | Captura Batch Semana 8; validacion.txt; BatchJobsRestartIntegrationTests; Week3PartitionedJobsIntegrationTests; etapa7-01-mvn-verify.txt | §8; instrucciones.md §3–4 | COMPLETO |
| C4 — 3 BFF optimizados y seguros | PENDIENTE DOCENTE: pauta no disponible | Web dashboard, Mobile resumen y ATM saldo/movimientos; payloads, JWT/TLS/fallback. | Tres módulos con límites distintos, relay, roles y Resilience4j. Retiro ATM sigue legacy. | etapa6-16-bff-legacy-regresion-final.json; etapa5-02-tls-vigente-https-health.json; etapa5-04-oauth-401-403.json; etapa5-05-mobile-resilience-503.json; etapa5-06-mobile-recuperacion-200.json | §9/11/13; instrucciones.md §6–7/12 | COMPLETO |
| C5 — 3 microservicios + Spring Cloud + Resilience4j + Kafka | PENDIENTE DOCENTE: pauta no disponible | Clientes; apertura/mantenimiento/cierre; depósitos/transferencias/pagos; Config/discovery/routing/fallback y eventos con propósito. | Customer/Account/Payment, @LoadBalanced, circuitos, outbox Account y auditoría/reconciliación Payment. | 05-customer.json; 06-account-apertura.json; 10-cierre-rollback.json; etapa6-06-flujo-financiero-escalado.json; 11-resilience-503.json; 12-resilience-recuperacion.json; 13-kafka-outbox-audit.json; 14-kafka-dlt.json | §10–14; instrucciones.md §8–12 | COMPLETO |
| C6 — Docker + escala horizontal + nube | PENDIENTE DOCENTE: pauta no disponible | Separar Docker, tres negocios escalables, 2+2+2 real y cloud. | Dockerfile/Compose y override, leases y group compartido; nube solo propuesta. | etapa6-03-replicas-docker.json; etapa6-04-eureka-2-2-2.json; etapa6-08-outbox-coordinada.json; etapa6-09-consumer-group-payment.json; etapa6-10-failover-account-service.json | §15/20; despliegue.md A/B | PENDIENTE DOCENTE |
| C7 — código / informe / instrucciones / despliegue | PENDIENTE DOCENTE: pauta no disponible | Código accesible, informe en plantilla y guías de ejecución/despliegue. | Código local eft; tres documentos raíz; borrador textual. Sin PDF/push aún. | git local; etapa7-01-mvn-verify.txt; inventario final | readme.md; instrucciones.md; despliegue.md; borrador técnico | PENDIENTE ENTREGA |
| C8 — video 5–7 min / cuatro puntos | PENDIENTE DOCENTE: pauta no disponible | Resumen ejecutivo; resultados/legacy; desafíos/soluciones; mejoras/próximos pasos. | Guion ~6 min con cuatro bloques, pantallas y transiciones; video no grabado. | guion-video.md; indice-evidencias-finales.md | guion-video.md y futuro enlace real | PENDIENTE ENTREGA |

## Desglose obligatorio C6

| Componente | Estado | Evidencia |
|---|---|---|
| Docker | COMPLETO | etapa6-03-replicas-docker.json; etapa6-12-compose-final.json |
| 3 microservicios escalables | COMPLETO | Customer/Account/Payment, estado compartido, claims y group Payment |
| Escalado real 2+2+2 | COMPLETO | etapa6-04-eureka-2-2-2.json, routing y failovers |
| Cloud/AWS real | PENDIENTE DOCENTE | Ninguna evidencia de despliegue; sección propuesta en despliegue.md B |

## Desglose C7 / C8

| Entregable | Estado |
|---|---|
| Código técnico y pruebas locales | COMPLETO |
| readme.md / instrucciones.md / despliegue.md raíz | COMPLETO |
| Borrador textual y diagramas | COMPLETO |
| Informe PDF en plantilla | PENDIENTE DOCENTE: plantilla; PENDIENTE ENTREGA: exportación |
| Rama y commit publicados en GitHub | PENDIENTE ENTREGA: no push autorizado |
| Guion cuatro puntos / 5–7 min | COMPLETO |
| Grabación y enlace de video | PENDIENTE ENTREGA |

C3 demuestra escalabilidad local/restart aislado, no Batch multiinstancia ni remote partitioning/capacidad ilimitada. Cada worker relee CSV; una JobInstance nueva puede duplicar salidas. C4 conserva roles/retirada ATM local, sin autorización de personas por titularidad. C5 usa BD física compartida y pago académico como débito; no adquirente ni ACID distribuido. C6 no demuestra HA de infraestructura ni SLA.

No proponer features nuevas sin exigencia de pauta. Confirmar texto/puntajes oficiales y alcance antes de entrega. Ver [gaps](gaps-entrega.md).
