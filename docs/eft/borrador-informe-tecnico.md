# Banco XYZ — borrador del informe técnico EFT

**Markdown preparatorio. No PDF final, no plantilla inventada.** Fecha de consolidación: 5 de octubre de 2026; corrección contra pauta oficial: 6 de octubre de 2026. Basado en código y evidencia Etapas 1–6; ver [matriz](matriz-rubrica-evidencias.md), [índice principal](indice-evidencias-finales.md) y [gaps](gaps-entrega.md).

## 1. Portada placeholder

[ADAPTAR A PLANTILLA DOCENTE CUANDO SEA ENTREGADA]. Banco XYZ — Evaluación Final Transversal, Desarrollo Backend III. [Autor/es, sección y docente: completar con datos oficiales]. No inventar formato, institución de portada o datos personales.

## 2. Resumen ejecutivo

Se modernizó el dominio operativo sobre la base Batch validada de Semana 8. Los tres BFF conservan contratos y datos históricos; Customer registra identidades declaradas, Account mantiene cuentas/saldos y Payment coordina depósito, transferencia y pago académico. PostgreSQL aporta transacciones locales; OAuth2/HTTPS protegen el acceso técnico; outbox/Kafka permiten auditoría y reconciliación.

La validación real cubrió Docker, TLS, OAuth, datos, idempotencia, fallback, DLT y seis réplicas de negocio simultáneas. El entorno volvió a 13 servicios healthy con Customer/Account/Payment 1+1+1. Etapa 7 añade documentación y verify, no una nueva demostración funcional. Pauta e instrucciones oficiales disponibles; cloud pendiente de ejecución en laboratorio docente y PDF pendiente de localizar plantilla oficial.

## 3. Contexto legacy

Los CSV contienen intereses, movimientos anuales y transacciones. No proporcionan identidad inequívoca de clientes, moneda, comercio ni contrapartes de transferencia. Nombres contradictorios impiden inferir titulares. La última salida de intereses sostiene el saldo legacy; ATM mantiene su retiro transaccional sobre ese dominio. Modernizar no permite reinterpretar automáticamente esos resultados como maestros operacionales.

Los procesos funcionales/dominios modernizados se agrupan en transacciones, intereses, movimientos anuales, cuentas/pagos y clientes/titularidad. Esta clasificación contextual no es la lista oficial de C1.

Fuente: [modelo de dominio](modelo-dominio.md). El job anual persiste movimientos; no genera por sí mismo un documento anual final.

## 4. Objetivos

Preservar tres jobs y tres canales; separar gestión Customer/Account/Payment; ejecutar finanzas sin duplicar efectos; configurar/descubrir servicios; probar degradación honesta y publicación durable; demostrar escala horizontal de tres negocios sin perder datos; preparar una entrega trazable.

No se incluyen login de personas, autorización por titularidad, migración automática de legacy, adquirente externo o cloud ejecutado.

## 5. Cinco procesos críticos oficiales de migración — C1 (10 puntos)

| Proceso oficial | Implementación y evidencia del proyecto |
|---|---|
| Migración de Procesos Batch a Spring Batch | Tres jobs: transaccionesDiariasJob, interesesMensualesJob y estadosCuentaAnualesJob; readers/processors/writers, errores, particiones y restart probado |
| División del Sistema Monolítico en Microservicios | Customer, Account y Payment con ownership lógico y servicios de plataforma; validación real y seis réplicas UP |
| Implementación del Patrón Backend for Frontend (BFF) | Web/Mobile/ATM independientes con payloads diferenciados, token relay y contratos históricos preservados |
| Implementación de Seguridad Distribuida con Spring Cloud Security | OAuth2/Spring Security, JWT verificado, HTTPS, roles por canal y scopes operacionales; no equivale a autorización de personas por titularidad |
| Integración de Mensajería Asíncrona con Apache Kafka | Anomalías Batch/Anomaly y eventos financieros: outbox Account, auditoría/reconciliación Payment, deduplicación, retry y DLT |

Corresponde a los cinco procesos nombrados por la pauta oficial. La clasificación funcional del contexto (§3) no sustituye C1. Evidencias concretas: [matriz](matriz-rubrica-evidencias.md) e [índice](indice-evidencias-finales.md).

## 6. Tres requerimientos clave — C2

**R1 Integridad y trazabilidad:** una transferencia debe debitar/acreditar de manera indivisible y un retry no debe repetir fondos. Account bloquea balances en orden y confirma saldo/posting/outbox en una transacción; Payment conserva operationId/key/hash y comprobantes.

**R2 Seguridad y adecuación al canal:** permisos distintos y payload proporcional. OAuth/JWT, TLS, roles Web/Mobile/ATM y scopes operacionales; tres BFF reducen/organizan información. La identidad técnica no equivale a titular.

**R3 Disponibilidad y escala:** una caída debe devolver un error controlado y permitir recuperación. Config/Eureka/LoadBalancer y Resilience4j desacoplan dependencias; réplicas de negocios comparten PostgreSQL y consumer group, con publishers coordinados. No implica HA de infraestructura ni un SLA.

## 7. Arquitectura propuesta e implementada

Ownership lógico: Customer → identidad explícita; Account → vínculos, ciclo de cuenta, balances y postings; Payment → solicitud/estado y auditoría; Batch → importación; Anomaly → anomalías. Comunicación registral/financiera por HTTP verificado y token relay; eventos financieros posteriores al commit.

La BD física PostgreSQL compartida simplifica el laboratorio, no materializa bases aisladas por microservicio. Los clientes BFF/negocios usan Config/Eureka; Auth/Batch/Anomaly conservan configuración propia.

[DIAGRAMA ARQUITECTURA GENERAL] Fuente disponible: [diagramas.md §1](diagramas.md#1-arquitectura-general).

## 8. Spring Batch — C3

Tres jobs: transaccionesDiariasJob, interesesMensualesJob y estadosCuentaAnualesJob. Readers CSV `StepScope` con checkpoint ItemStream, processors de validación/dominio y writers JDBC; transacciones añade outbox mediante writer compuesto.

Chunk transaccional; skip de errores de parsing/dominio hasta 1000 por worker y retry transitorio con tres intentos. Fallos no clasificados o agotamiento llevan a FAILED; chunks previos permanecen confirmados. Runner detiene la cadena de jobs si uno no termina COMPLETED.

Partitioner por rangos + ThreadPoolTaskExecutor permiten cuatro workers en tests de Semana 3. Validaciones de configuración y tests de restart de los tres jobs acreditan rollback de chunk, checkpoint, misma JobInstance y rechazo de ejecución completa repetida. Captura histórica muestra tres COMPLETED; verify vigente vuelve a ejecutar las pruebas.

Límites: cada worker relee el CSV; prelectura falla antes del skip; límite de rechazo por worker; timestamp genera instancia nueva capaz de duplicar salidas. Restart probado con H2 aislado/worker único, no como recuperación PostgreSQL multiworker runtime. Batch no se escaló por Compose. Fuentes: [auditoría](auditoria-inicial.md), [validación](validacion.txt) y evidencia Batch del [índice](indice-evidencias-finales.md).

## 9. BFF — C4

Web entrega dashboard con titular, saldos/tasa, hasta 20 movimientos y 10 anomalías; estas son globales. Mobile ofrece resumen y cinco movimientos sin descripción. ATM expone saldo y tres movimientos esenciales; retiro continúa en lógica/BD legacy.

JWT/roles por canal, HTTPS, token relay, discovery, timeouts y fallback 503. Etapa 5 demostró los tres 200/401/403 y Mobile 200→503→200; Etapa 6 comprobó respuestas completas iguales tras recreación/escala. Las cuentas maestras modernas no fueron forzadas en estos contratos.

## 10. Microservicios — C5

Customer: UUID/name/version, alta/consulta/actualización con concurrencia optimista y consulta remota de cuentas. Sin identidad inferida desde nombres.

Account: alta ACTIVE/saldo 0, titulares explícitos, consulta, mantenimiento versionado y cierre CLOSED que retiene registro/saldo. Bloquea IDs legacy y no liquida fondos al cerrar. Posting financiero interno con idempotencia y locks ordenados.

Payment: depósito, transferencia y pago como débito académico; operación PENDING persistida antes del HTTP, COMPLETED con comprobante o FAILED por rechazo determinista. Indisponibilidad deja PENDING/503 y permite recuperar la misma key. APIs/errors/scopes exactos: [contratos vigentes](contratos-servicios.md).

## 11. Seguridad

Authorization Server emite JWT RS256 por client_credentials. Resource servers validan firma/issuer/audience/tiempo y autorización; dominios requieren scopes, BFF roles por canal. Secretos/stores fuera de Git.

TLS local verifica cadena y SAN; Etapa 5 recargó BFF que seguían sirviendo certificado anterior. En escala, el transporte verifica DNS lógico mientras conecta a IP Eureka. Config/Eureka/PostgreSQL/Kafka internos no tienen la protección de red/PKI productiva exigible para cloud; no confundir laboratorio con seguridad productiva.

## 12. Configuración y discovery

Config native distribuye configuración por aplicación; Eureka registra BFF/negocios; RestClient @LoadBalanced usa service-id. Durante escala se anunciaron seis IDs/IPs distintos y puertos seguros. Se eliminó la segunda caché LoadBalancer del override para evitar rutas obsoletas adicionales.

El runner usa selección round-robin de registros Eureka y curl --resolve para conservar hostname TLS; es cliente de pruebas. Los access logs de ambas réplicas, incluido Payment→Account, acreditan tráfico efectivo; no añadir instanceId a DTO financieros.

## 13. Resilience4j

Circuitos protegen BFF→Account, Account→Customer, Customer→Account y Payment→Account. No se inventan saldos ni listas vacías ante caída. La prueba Mobile detuvo solo Account y obtuvo 503, luego 200 tras restaurar. Payment conservó PENDING sin posting y recuperó por replay/evento.

Las transiciones internas de circuito se comprueban además con tests. La evidencia runtime demuestra indisponibilidad/fallback/recuperación; no atribuir estado de circuito que una captura no expone.

## 14. Kafka y consistencia distribuida

Batch conserva su outbox/anomalías/Anomaly/retry/DLT. Flujo financiero: saldo(s)+posting+outbox en transacción Account; publisher reclama filas y espera ack; Payment consume para auditoría/reconciliación, no mueve fondos.

[DIAGRAMA FLUJO PAYMENT] Fuente: [diagramas.md §2](diagramas.md#2-payment--account--kafka--auditoríareconciliación).

Topic financiero: tres particiones/RF1 local; group financial-payment-audit; replay de eventId sin otra auditoría. JSON inválido llegó a DLT y el consumo continuó. No se forzó caída del broker/DB compartidos para demostrar retry transitorio runtime.

Lease/fencing: PostgreSQL SKIP LOCKED, owner/token nuevos, PROCESSING, lease/retry y updates condicionados. Recuperación de claims vencidos y rechazo de token viejo cubiertos por tests PostgreSQL reales. At-least-once: caída entre ack y update DB puede repetir evento; dedup lógica, no exactly-once físico. No hay ACID entre Account y Payment.

## 15. Docker y escalabilidad horizontal — C6

Dockerfile parametrizado construye módulos; base 13 servicios. Override escala Customer/Account/Payment 2+2+2, elimina binding host Account y mantiene infraestructura/BFF singleton. Certificados vigentes; seis UP; requests en ambas réplicas; ambos publishers contribuyen; dos consumidores repartieron tres particiones y lag 0.

Failover individual mantuvo GET 200 y operación financiera 200/201. Se restauró cada contenedor y finalmente base 1+1+1 con host Account 8085. Datos/volumen/IDs de infraestructura preservados.

[DIAGRAMA ESCALABILIDAD] Fuente: [diagramas.md §3](diagramas.md#3-escalado-local-222). Procedimiento: [despliegue raíz](../../despliegue.md). Cloud queda PENDIENTE DE EJECUCIÓN EN LABORATORIO DOCENTE; la escala local no acredita despliegue cloud.

## 16. Validación real

Etapa 4: APIs modernas, PostgreSQL real, OAuth/TLS, pagos/idempotencia, cierre/rollback, caída/recuperación y Kafka/DLT. Etapa 5: BFF certificado vigente, contratos y resiliencia. Etapa 6: réplicas/routing/claims/group/failover/preservación, retorno y verify.

Etapa 7: mvn verify BUILD SUCCESS, 256 tests actuales, cero fallos/errores/skipped; seis PostgreSQL habilitados. Cuatro KafkaReal heredados retornan sin flag externo: broker real acreditado por runners previos. Conteo desde suites del log actual para excluir un XML residual antiguo; no falsear total.

## 17. Resultados y comparación con legacy

| Antes | Resultado EFT | Límite |
|---|---|---|
| Identidad basada solo en nombres CSV | Cliente UUID explícito | No migración automática ni IAM de persona |
| Saldos sobre salida Batch | Ledger/saldo operacional maestro | BFF legacy siguen fuente anterior |
| Históricos de transacciones, no órdenes de pago | Depósitos/transferencias/pagos y comprobantes | Pago académico sin adquirente |
| Publishers sin coordinación horizontal financiera | Claims/lease y dos publishers | At-least-once |
| Routing indistinguible por DNS compartido | IPs/IDs y tráfico por réplica | No SLA/infraestructura HA |

Run Etapa 6: 25 postings/COMPLETED/outboxes PUBLISHED/audits; saldos 101/20. Once tablas preservaron filas anteriores; tres BFF conservaron respuestas. No sumar conteos de etapas como si pertenecieran a un solo run.

## 18. Problemas encontrados y soluciones

Imagen Account antigua no incluía SQL nuevo: reconstrucción dirigida. BFF aún servían TLS anterior: recreación de tres BFF usando material vigente. Dos publishers podían seleccionar la misma fila: claim/lease/token DB. IPs discovery no estaban en SAN: verificar identidad DNS lógica manteniendo cadena. Failover Account reveló caché obsoleta/503 Payment: deshabilitar segunda caché en override y retry idempotente a otra réplica. La misma operación PENDING completó con un posting; no corregir resultados mediante SQL destructivo.

## 19. Limitaciones

Pauta e instrucciones oficiales conocidas, con máximo 100 puntos. Plantilla PDF pendiente de localizar; laboratorio cloud disponible pero sin ejecutar; PDF/video/publicación pendientes. Batch local con idempotencia de nuevos lotes pendiente. BFF/ATM permanecen legacy sin autorización por titularidad; DB compartida y servicios de infraestructura singleton. Ningún benchmark limitado prueba capacidad ilimitada. Claims abandonados comprobados en tests reales, no captura runtime de la ventana exacta de muerte.

## 20. Preparación cloud

**Despliegue cloud — pendiente de ejecución en laboratorio docente. Procedimiento conceptual propuesto; no ejecutado todavía.**

Imágenes/registry, orquestación, secretos/PKI, PostgreSQL y Kafka gestionados, Config/discovery, ingress, health, logs, migraciones exclusivas, variables, escala, backups/rollback y red se detallan en [despliegue.md B](../../despliegue.md). Existe una invitación a laboratorio docente para despliegue real. Los pasos conceptuales AWS se conservarán como referencia y se sustituirán/complementarán con el procedimiento efectivamente ejecutado. No hay recursos, IDs o resultados inventados; no se diseña ni ejecuta cloud en Etapa 7.1. No extrapolar el inicializador local a arranques concurrentes productivos.

## 21. Mejoras futuras

Localizar plantilla, ejecutar laboratorio cloud en una etapa autorizada y completar procedimiento/evidencias reales; preparar PDF/video y publicación autorizada. Integración BFF moderna, identidad por titularidad, identidad natural de lotes y HA/benchmark requieren alcance propio; no añadirlos para inflar la evaluación.

## 22. Conclusiones

La modernización preserva el funcionamiento legacy y añade un dominio financiero trazable. Evidencia real y tests sustentan APIs, TLS/OAuth, degradación controlada, eventos y escala de tres negocios. Se distinguen implementación demostrada, límites técnicos y entrega pendiente. La preparación no equivale a publicación ni a cloud realizado.

## 23. Evidencias y referencias

[20 evidencias principales](indice-evidencias-finales.md); [matriz C1–C8](matriz-rubrica-evidencias.md); [auditoría documental](auditoria-documental-final.md); [gaps](gaps-entrega.md); [diagramas](diagramas.md); [guion video](guion-video.md). Informes de etapas conservan detalles y fechas, no se copian íntegros.

Fuentes técnicas primarias para claims, TLS, retries y preparación cloud se citan en los informes de escala y despliegue. Fuentes oficiales leídas: PBY2203_EFT_S9_Pauta_de_evaluación_EFT y PBY2203_EFT_S9_Instrucciones_específicas (forma A). Plantilla PBY2203_EFT_S9_plantilla_PDF pendiente de localizar; adaptar/exportar después.

Requisitos C7/C8 conocidos: readme.md, informe PDF, instrucciones.md y despliegue.md junto al video **MP4, 5–7 minutos, webcam y evidencias, cuatro puntos**, en una misma carpeta. No se han generado ni grabado las piezas finales.
