# Banco XYZ — Evaluación Final Transversal

Modernización académica del banco legacy para Desarrollo Backend III. Conserva los tres procesos Batch y canales Web/Mobile/ATM; incorpora Customer, Account y Payment con persistencia operacional, OAuth2, HTTPS, resiliencia y eventos Kafka.

Objetivo: separar responsabilidades, ejecutar operaciones financieras idempotentes y demostrar tres servicios de negocio escalables sin perder los contratos históricos.

Arquitectura: BFF → Account legacy; Payment → Account → Customer para el dominio nuevo; Account → outbox → Kafka → auditoría/reconciliación Payment. Batch → outbox → Kafka → Anomaly. Config Server/Eureka coordinan los clientes de negocio y los BFF.

Tecnologías: Java 21, Maven, Spring Boot 3.3.4, Spring Cloud 2023.0.6, Spring Batch, Spring Security/Authorization Server, Resilience4j, PostgreSQL 16.4, Kafka 3.8.0 y Docker Compose. Reactor: 12 módulos (Core, Batch, Config, Discovery, Auth, Account, Customer, Payment, Anomaly y tres BFF).

- Repositorio: [STGO-SV/Exp1_S3_Batch](https://github.com/STGO-SV/Exp1_S3_Batch).
- Evaluación local: rama `eft`; base técnica validada `a31c798`. Evaluar el HEAD documental posterior de esta rama.
- **Publicación pendiente:** no se hizo push; las referencias remotas locales no contienen `origin/eft`. Completar enlace a rama y SHA exacto del cierre cuando se autorice el push final. El enlace actual al repositorio no prueba que la EFT esté publicada.
- [Instrucciones](instrucciones.md), [despliegue](despliegue.md), [borrador técnico](docs/eft/borrador-informe-tecnico.md), [matriz de rúbrica](docs/eft/matriz-rubrica-evidencias.md).
- [Evidencias priorizadas](docs/eft/indice-evidencias-finales.md); archivos en `docs/evidence/eft/` y Batch histórico en `docs/evidence/semana-8/`.
- Prueba principal desde raíz: `mvn verify`. Con PostgreSQL Compose preparado: `./scripts/test-eft-scale.ps1 -TestMode Verify -LogName evaluacion-verify.log` habilita también los seis tests PostgreSQL aislados.
- Validado: HTTPS/OAuth, operaciones/idempotencia, Kafka/DLT, resiliencia y 2+2+2; entorno restaurado a 1+1+1. Cloud: PENDIENTE DE EJECUCIÓN EN LABORATORIO DOCENTE disponible; plantilla PDF: pendiente de localizar; PDF/video: pendientes de entrega. Pauta e instrucciones oficiales conocidas (100 puntos).

Secretos, `.env`, claves privadas y `.local/` no están versionados. El inicializador crea/reutiliza material local; no publicar tokens ni logs crudos.

El [README de Semana 8](README-semana-8.md) se conserva íntegro como documentación histórica y no constituye el entregable final EFT.
