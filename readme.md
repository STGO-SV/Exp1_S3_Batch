# Banco XYZ — Evaluación Final Transversal

Modernización académica del banco legacy para Desarrollo Backend III. El proyecto conserva tres procesos Spring Batch y los BFF Web/Mobile/ATM, e incorpora Customer Service, Account Service y Payment Service con operaciones financieras idempotentes, seguridad y recuperación ante fallos.

## Arquitectura y tecnologías

Los BFF consultan el dominio Account legacy. Payment Service delega operaciones atómicas a Account Service, que consulta Customer Service y registra postings y outbox. Kafka transporta eventos financieros hacia auditoría/reconciliación Payment y eventos Batch hacia Anomaly. Config Server y Eureka proporcionan configuración y descubrimiento.

Java 21, Maven, Spring Boot 3.3.4, Spring Cloud 2023.0.6, Spring Batch, Spring Security/Authorization Server, OAuth2/JWT, HTTPS, Resilience4j, PostgreSQL 16.4, Kafka 3.8.0 y Docker Compose. El reactor contiene 12 módulos: Core, Batch, Config, Discovery, Auth, Account, Customer, Payment, Anomaly y tres BFF.

## Estado final y entregables

La EFT está integrada en main y publicada en [GitHub: STGO-SV/Exp1_S3_Batch](https://github.com/STGO-SV/Exp1_S3_Batch). El desarrollo técnico está completo. El informe PDF fue generado con la plantilla oficial PBY2203_EFT_S9_plantilla_PDF.docx y existe una versión DOCX editable. El video fue grabado y se entrega fuera del repositorio.

- [Instrucciones de preparación, ejecución y pruebas](instrucciones.md).
- [Despliegue local y entorno AWS EC2](despliegue.md).
- [Fuentes editables de los diagramas](docs/eft/diagramas.md).
- [Evidencia principal EFT](docs/evidence/eft/).
- [Evidencia Batch: tres jobs COMPLETED](docs/evidence/semana-8/semana-8.evidencia-batch-3-jobs-status-COMPLETED.png).
- [Validación Maven: 256 pruebas reportadas y BUILD SUCCESS](docs/evidence/eft/etapa7-1-01-mvn-verify.txt).

## Resultados y límites

El entorno AWS EC2 utilizó Amazon Linux 2023 y Docker Compose sobre una única instancia EC2: [13 servicios base saludables](docs/evidence/eft/capturas/1_despliegue_contenedores_nube.png), [16 contenedores durante la prueba escalada 2+2+2](docs/evidence/eft/capturas/aws-escalabilidad-horizontal-2x2x2.png), [Eureka con dos instancias UP por servicio](docs/evidence/eft/capturas/aws-eureka-2x2x2-up.png) y failover funcional. Al finalizar la prueba se restauró una réplica por servicio de negocio.

La escalabilidad horizontal se demostró a nivel de contenedores. No se acredita HA multi-host/multi-AZ ni Kubernetes. Las capturas AWS con curl -k demuestran respuestas HTTPS, sin validación estricta de CA/hostname. La [regresión local BFF](docs/evidence/eft/etapa6-16-bff-legacy-regresion-final.json) y la [validación TLS local](docs/evidence/eft/etapa5-02-tls-vigente-https-health.json) conservan su alcance propio.

La prueba principal es mvn verify. Con PostgreSQL Compose preparado, ./scripts/test-eft-scale.ps1 -TestMode Verify -LogName evaluacion-verify.log habilita también seis pruebas PostgreSQL aisladas. Los datos académicos y las precondiciones de los runners se describen en instrucciones.md.

Los BFF mantienen contratos legacy; la autorización identifica clientes técnicos, sin acreditar titularidad personal. PostgreSQL es compartido y el paralelismo Batch es local. Secretos, .env, claves privadas y .local/ no están versionados; la evidencia registrada excluye tokens y secretos.
