# Matriz de rúbrica → implementación → evidencia → documento

Fuentes de verdad disponibles y leídas en Etapa 7.1 (2026-10-06): **PBY2203_EFT_S9_Pauta_de_evaluación_EFT.docx** y **PBY2203_EFT_S9_Instrucciones_específicas (forma A).docx**, carpeta académica S9-EFT. Los ocho puntajes máximos suman **100 puntos**. Esta matriz registra máximos y cobertura/evidencia; no adjudica notas ni sustituye evaluación docente.

COMPLETO describe cobertura técnica dentro del alcance documentado; conserva límites Batch/BFF/dominio. Evidencias sin prefijo corresponden a docs/evidence/eft; se priorizan en el [índice](indice-evidencias-finales.md). Secciones § corresponden al borrador-informe-tecnico.md. Los requisitos C1–C8 se contrastaron con los descriptores oficiales de logro completo.


| Criterio | Puntaje máximo | Requisito disponible | Implementación | Evidencia / archivo concreto | Documento final | Estado |
|---|---|---|---|---|---|---|
| C1 — 5 procesos críticos | 10 puntos | Identifica los 5 procesos críticos para migración a microservicios: Batch a Spring Batch; división del monolito; BFF; seguridad distribuida con Spring Cloud Security; mensajería asíncrona Kafka. | Tres jobs Spring Batch; Customer/Account/Payment y plataforma; Web/Mobile/ATM; OAuth2/JWT/TLS/roles/scopes; anomalías y eventos financieros/outbox/audit/reconciliation. | borrador-informe-tecnico.md §5; validacion.txt; etapa6-16-bff-legacy-regresion-final.json; etapa5-04-oauth-401-403.json; 13-kafka-outbox-audit.json; etapa6-04-eureka-2-2-2.json | §5 | COMPLETO |
| C2 — 3 requerimientos clave y justificación arquitectónica | 15 puntos | Desarrolla la propuesta de arquitectura identificando 3 requerimientos clave del negocio y justificando cada decisión. | Ledger/idempotencia/outbox; OAuth/TLS/BFF; Config/Eureka/Resilience4j/escala. | etapa6-07-idempotencia.json; etapa5-04-oauth-401-403.json; etapa6-04-eureka-2-2-2.json | §6–7 | COMPLETO |
| C3 — 3 procesos Spring Batch con errores, paralelismo y escalabilidad | 15 puntos | Implementa 3 procesos batch en Spring Batch, con manejo avanzado de errores, paralelismo y escalabilidad. | BatchJobsConfig/PartitioningConfig, readers ItemStream, tres restart tests y cuatro workers. | Captura Batch Semana 8; validacion.txt; BatchJobsRestartIntegrationTests; Week3PartitionedJobsIntegrationTests; etapa7-01-mvn-verify.txt | §8; instrucciones.md §3–4 | COMPLETO |
| C4 — 3 BFF optimizados y seguros | 15 puntos | Desarrolla BFF para los 3 canales, optimizando rendimiento y seguridad por plataforma. | Tres módulos con límites distintos, relay, roles y Resilience4j. Retiro ATM sigue legacy. | etapa6-16-bff-legacy-regresion-final.json; etapa5-02-tls-vigente-https-health.json; etapa5-04-oauth-401-403.json; etapa5-05-mobile-resilience-503.json; etapa5-06-mobile-recuperacion-200.json | §9/11/13; instrucciones.md §6–7/12 | COMPLETO |
| C5 — 3 microservicios + Spring Cloud + Resilience4j + Kafka | 15 puntos | Crea microservicios para los 3 servicios clave con resiliencia y seguridad usando Spring Cloud, Resilience4j y Kafka. | Customer/Account/Payment, @LoadBalanced, circuitos, outbox Account y auditoría/reconciliación Payment. | 05-customer.json; 06-account-apertura.json; 10-cierre-rollback.json; etapa6-06-flujo-financiero-escalado.json; 11-resilience-503.json; 12-resilience-recuperacion.json; 13-kafka-outbox-audit.json; 14-kafka-dlt.json | §10–14; instrucciones.md §8–12 | COMPLETO |
| C6 — Docker + escala horizontal + nube | 10 puntos | Utiliza Docker y escalabilidad horizontal para despliegue y configuración de 3 microservicios en la nube. | AWS EC2 Amazon Linux 2023 + Docker Compose: base 13 healthy, Customer/Account/Payment 2+2+2, 16 contenedores y Eureka 2 UP por servicio; failover funcional/recuperación sobre un único host. | [Base EC2](../evidence/eft/capturas/1_despliegue_contenedores_nube.png); [2+2+2](../evidence/eft/capturas/aws-escalabilidad-horizontal-2x2x2.png); [Eureka](../evidence/eft/capturas/aws-eureka-2x2x2-up.png); [failover Account](../evidence/eft/capturas/aws-failover-account-service-bff-200.png); capturas Customer/Payment y recuperación en índice | §15/20; despliegue.md A/B | COMPLETO: despliegue/escala en nube de un único host; no HA multi-host |
| C7 — código / informe / instrucciones / despliegue | 10 puntos | Documenta los 4 aspectos: código fuente, informe técnico, instrucciones y pasos de despliegue. | Código local eft; readme/instrucciones preparados; despliegue local/AWS documentado con capturas y límites; borrador preparado, Plantilla oficial PBY2203_EFT_S9_plantilla_PDF.docx identificada y disponible; PDF final generado y listo para revisión, con versión DOCX editable. | git local; etapa7-01-mvn-verify.txt; inventario final | readme.md; instrucciones.md; despliegue.md; borrador técnico | PDF/DOCX GENERADOS; CIERRE/PUBLICACIÓN PENDIENTE |
| C8 — video 5–7 min / cuatro puntos | 10 puntos | Video respetando formato/tiempo y exponiendo los 4 puntos solicitados. | Pauta/requerimientos conocidos; guion ~6 min, MP4 con webcam/evidencias y cuatro bloques; video por grabar. | guion-video.md; indice-evidencias-finales.md | guion-video.md y futuro enlace real | PENDIENTE ENTREGA |

## Desglose obligatorio C6

| Componente | Estado | Evidencia |
|---|---|---|
| Docker | COMPLETO | etapa6-03-replicas-docker.json; etapa6-12-compose-final.json |
| 3 microservicios escalables | COMPLETO | Customer/Account/Payment, estado compartido, claims y group Payment |
| Escalado real 2+2+2 | COMPLETO | etapa6-04-eureka-2-2-2.json, routing y failovers |
| Despliegue cloud AWS EC2 | COMPLETO | [13 healthy](../evidence/eft/capturas/1_despliegue_contenedores_nube.png), [2+2+2/16](../evidence/eft/capturas/aws-escalabilidad-horizontal-2x2x2.png), [Eureka](../evidence/eft/capturas/aws-eureka-2x2x2-up.png); escala de contenedores en una única EC2 |

## Desglose C7 / C8

| Entregable | Estado |
|---|---|
| Código técnico y pruebas locales | COMPLETO |
| readme.md | PREPARADO |
| instrucciones.md | PREPARADO |
| despliegue.md | PREPARADO: local y AWS EC2 ejecutado documentados; aprovisionamiento no reconstruido a partir de datos ausentes |
| Borrador textual y diagramas | COMPLETO |
| Informe técnico | PDF FINAL GENERADO Y LISTO PARA REVISIÓN; DOCX editable generado con la plantilla oficial |
| Rama y commit publicados en GitHub | PENDIENTE ENTREGA: no push autorizado |
| Guion cuatro puntos / 5–7 min | COMPLETO |
| Video MP4 5–7 min con webcam/evidencias y cuatro puntos | PENDIENTE ENTREGA: guion preparado; grabación/enlace pendientes |

C3 demuestra escalabilidad local/restart aislado, no Batch multiinstancia ni remote partitioning/capacidad ilimitada. Cada worker relee CSV; una JobInstance nueva puede duplicar salidas. C4 conserva roles/retirada ATM local, sin autorización de personas por titularidad. C5 usa BD física compartida y pago académico como débito; no adquirente ni ACID distribuido. C6 no demuestra HA de infraestructura ni SLA.

No proponer features nuevas sin exigencia de pauta. Pauta y puntajes conocidos: despliegue cloud ya ejecutado; revisar el informe PDF ya generado con la plantilla oficial y su DOCX editable. C7 conserva pendiente el cierre/publicación; PDF/DOCX ya generados. Ver [gaps](gaps-entrega.md).

## C1: cinco procesos oficiales y distinción funcional

1. Migración de Procesos Batch a Spring Batch.
2. División del Sistema Monolítico en Microservicios.
3. Implementación del Patrón Backend for Frontend (BFF).
4. Implementación de Seguridad Distribuida con Spring Cloud Security.
5. Integración de Mensajería Asíncrona con Apache Kafka.

Transacciones, intereses, movimientos anuales, cuentas/pagos y clientes/titularidad son **procesos funcionales/dominios modernizados**, no la respuesta a C1. Su contextualización se conserva en el borrador §3. Total máximo: 10+15+15+15+15+10+10+10 = **100**.

## Requisitos de entrega oficiales conocidos

readme.md (enlace GitHub), informe PDF mediante PBY2203_EFT_S9_plantilla_PDF, instrucciones.md y despliegue.md cloud; junto al video MP4 de 5–7 minutos, con webcam/evidencias y cuatro puntos, **en una misma carpeta**. La plantilla oficial PBY2203_EFT_S9_plantilla_PDF.docx ya fue utilizada para generar el PDF final y el DOCX editable. Esta corrección solo actualiza documentación; el video y el cierre/publicación siguen pendientes.

Actualización AWS (2026-10-08): [smoke OAuth/Customer/Account](../evidence/eft/capturas/3_smoke_test_funcional_200_201_201.png) y [Web dashboard](../evidence/eft/capturas/aws-smoke-web-bff-200-dashboard.png). Recuperación 2+2+2 capturada; retorno final 1+1+1 informado por el usuario, sin vista consolidada de ese cierre. Requests AWS con curl -k: no acreditar verificación TLS estricta ni SLA con estas capturas. Máximos de pauta y límites de otros criterios se mantienen.
