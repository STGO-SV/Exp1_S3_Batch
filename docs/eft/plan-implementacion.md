> Estado vigente (2026-10-09): La plantilla oficial PBY2203_EFT_S9_plantilla_PDF.docx fue localizada y utilizada. El informe técnico final PDF está generado y listo para revisión; existe una versión DOCX editable, según confirmación del usuario. AWS está ejecutado y documentado, con sus capturas incorporadas. El único entregable principal pendiente es el video MP4 de 5–7 minutos; después corresponde el cierre/publicación autorizado del repositorio. C6 cloud completo en una única EC2; sin HA multi-host/multi-AZ. Fases anteriores conservan historia. Ver [gaps actuales](gaps-entrega.md).

# EFT — plan de implementación

Base: 76b9773. Rama: eft. Mantener commits pequeños, sin push/merge hasta revisión.
El plan original no presuponía AWS ni plantilla PDF. Estado vigente: ambos pasos están completados; PDF y DOCX editable generados, video por grabar y posterior cierre/publicación. Esta corrección solo actualiza Markdown.

## Fase 1 — auditoría y base técnica (esta etapa)

- Matriz inicial histórica, clasificación de procesos funcionales/dominios y brechas. C1 vigente usa cinco procesos oficiales, no esta clasificación.
- Payment/Customer en reactor con Config/Eureka/JWT/HTTPS/health y tests; sin negocio inventado.
- Validación de paralelismo Batch y pruebas reales de rollback/restart.
- Compose opcional para scaffolding y puertos efímeros; base Semana 8 intacta.
- mvn verify y revisión de secretos/archivos.

Salida: cambios locales revisables y evidencia acotada; no afirmar cumplimiento completo EFT.

## Fase 2 — decisiones de dominio prioritarias

1. Cuenta maestra y saldo operativo: identidad, moneda, estado, apertura/cierre/mantenimiento, qué representa interes_procesado y cómo convivirá con Batch.
2. Cliente: fuente/identificador estable, datos mínimos disponibles, titularidad conjunta y relación cuenta-cliente. No deduplicar por nombre.
3. Pago/transferencia/depósito: contrapartes, límites, estados, clave de idempotencia y ledger/atomicidad.
4. Identidad del usuario en OAuth2: client_credentials identifica canal; acordar acceso por titular/recurso y scopes.
5. Periodo/fingerprint/identificador de origen por job; política ante duplicados/rechazos.

Estas decisiones necesitan información funcional del usuario/enunciado; no dependen necesariamente del docente sobre AWS o PDF. Salida: contratos y migraciones propuestas, revisadas antes de aplicarlas.

## Fase 3 — Gestión de Cuentas

- Introducir esquema/migraciones acordadas y capa de comandos preservando consultas actuales.
- Apertura, cierre y mantenimiento con estados y validaciones; autorización por recurso.
- Concurrencia optimista/pesimista y pruebas PostgreSQL de invariantes.
- No habilitar simplemente escritura sobre la tabla de intereses como sustituto de un dominio de cuentas.
- Evento de ciclo de vida mediante outbox cuando exista consumidor justificado.

Salida: contratos reales, casos negativos y compatibilidad BFF.

## Fase 4 — Payment y retiro ATM

- Implementar primero una operación real según contratos aprobados.
- Idempotency-key persistente y estados observables; fallo nunca devuelve éxito.
- Reutilizar reglas y pruebas de retiro/bloqueo; migrar ATM desde acceso directo solo con prueba de equivalencia.
- Transferencia debe conservar débito/crédito atómico según ownership acordado; no añadir saga por obligación aparente.
- Llamada a Account con timeout/Resilience4j y fallback de rechazo temporal.
- Outbox y PaymentCompleted/TransferCompleted con consumidores útiles y dedup.

Salida: pruebas de concurrencia, repetición de request, rollback, broker caído y compatibilidad.

## Fase 5 — Customer

- Modelo mínimo basado en fuente acordada; vínculo explícito a cuentas.
- Consultas/actualización validada y autorización de datos personales.
- CustomerProfileUpdated solo si Account/BFF necesitan proyección.
- Resilience4j únicamente donde exista llamada remota necesaria; degradación de campos opcionales, no identidad/autorización.

Salida: perfiles reales y pruebas de seguridad; no llamar a un shell “servicio completo”.

## Fase 6 — robustez y escala Batch

- Parámetros estables por periodo/archivo y procedimiento de restart conservando inputs.
- Test de restart con múltiples workers, PostgreSQL real y recuperación de particiones completadas.
- Definir límites globales/por partición y cuarentena auditable para errores de lectura sin ID.
- Pruebas de retry en workers reales, agotamiento/skip-limit y backoff configurable si se justifica.
- Benchmark grande con distribución sesgada; diseñar archivos preparticionados/staging o reader por rangos sin P escaneos.
- Revisar claves únicas solo después de acordar identidad de movimientos y periodos.
- Outbox claim/lease para publishers múltiples y migraciones de metadata Batch.
- No introducir remote partitioning si el benchmark no justifica su complejidad.

Salida: reconciliación, comparación de rendimiento y operación recuperable.

## Fase 7 — integración y demostración horizontal local

- Construir imágenes de nuevos módulos y hacer smoke tests del perfil eft.
- Extender SAN TLS con renovación controlada; validar confianza sin --insecure.
- Definir endpoints únicos TLS por réplica para discovery y demostrar distribución real.
- Ejecutar Compose opcional con tres réplicas de al menos tres microservicios completos.
- Registrar contenedores/health/IDs, llamadas por réplica y failover con carga.
- Provisionar topics con particiones acordadas; no asumir tres por disponer de un script histórico.
- Resolver identidad de consumers; no escalar publisher Batch sin lease.
- Añadir proxy/gateway solo cuando haga falta endpoint externo único.

Salida: evidencia local reproducible y tests end-to-end OAuth2/BFF/Kafka/PostgreSQL.

## Fase 8 — preparación cloud y entrega

- Mantener propuesta independiente de proveedor: datos persistentes, secretos, TLS, observabilidad, readiness, cuotas y HA.
- Si docente confirma preparación únicamente: documentar decisiones y procedimiento, sin desplegar.
- Si exige despliegue real: elaborar plan concreto y solicitar autorización de recursos/costos antes de ejecutar.
- Revisar el PDF final generado con la plantilla oficial y el DOCX editable; no inventar datos personales.
- Informe PDF generado; video MP4 de 5–7 minutos pendiente de grabación y verificación.

## Validación por cambio

Usar tests focalizados de módulos durante desarrollo; mvn verify al cerrar cada etapa.
No reemplazar evidencia nueva con capturas históricas. Mantener OAuth2, BFF, jobs, PostgreSQL, Config/Eureka, Resilience4j, outbox/Kafka/retry/DLT y Compose.
No versionar .env/.local/privados/certificados. Revisar diff y archivos explícitos antes de cada commit.
## Estado después de Etapa 2

Modelo y contratos detallados: modelo-dominio.md y contratos-servicios.md.
Se implementó maestro registral aditivo de Account/Customer, vínculos explícitos, concurrencia/idempotencia administrativa y dos llamadas REST resilientes.
Customer se integró al Compose base; Payment continúa como scaffold opcional.
Hay 207 casos reportados por verify, sin fallos; cuatro casos de Kafka externo siguen sin ejecutar integración.
Saldo maestro/migración, identidad real/titularidad legacy y cierre financiero quedan para revisión humana antes de Payment.
El ciclo registral no equivale a gestión financiera completa ni a operación de las cuentas nuevas desde ATM/BFF.
La siguiente fase prioritaria es revisar las decisiones concretas de modelo-dominio.md; no pasar directamente a despliegue/escala.
## Actualización Etapa 3
Decisiones de saldo maestro y ejecución financiera aprobadas. Implementados saldo operacional Account, comprobantes idempotentes, depósito/transferencia/pago, Payment persistente, outbox Account y consumidor de auditoría/reconciliación Payment.
SQL inicializador aditivo, compatibilidad legacy y scopes de canal conservados. Payment integra Compose base con OAuth financiero separado.
Evidencia final en informe-etapa-3.md. Docker no disponible: pendiente smoke real con daemon activo.
Próxima etapa: validar flujos en PostgreSQL/Compose, conectar BFF a maestros modernos según contrato aprobado y preparar escalado con coordinación de outbox; no ejecutarlo en esta etapa.

Cierre Etapa 3: mvn verify BUILD SUCCESS, 245 tests reportados sin fallos/errores; cuatro pruebas KafkaReal sin broker externo habilitado. Compose validado estructuralmente; daemon sigue detenido. Informe y lista completa: informe-etapa-3.md; conteos/evidencia: validacion-etapa3.txt.

## Cierre Etapa 4 — ejecución real

Validado Compose/PostgreSQL 16.4/HTTPS con certificado verificado/OAuth/Customer/Account/Payment/Kafka real.
Imagen Account antigua corregida reconstruyéndola; SQL empaquetado y ocho tablas EFT inicializadas por servicios.
Credencial financiera completada; secretos/JWT previos preservados. TLS regenerado solo porque faltaban SAN Customer/Payment.
Flujos financieros, idempotencia, cierre/rollback,503/PENDING/recuperación HTTP y reconciliación Kafka ejecutados. Cinco postings/outboxes PUBLISHED/audits, sin duplicación.
DLT real y continuidad después de mensaje inválido confirmadas; retries transitorios de infraestructura cubiertos por tests, sin fallo forzado de DB/Kafka compartidos.
mvn verify final BUILD SUCCESS:245 tests reportados,0 fallos/errores; cuatro KafkaReal heredados sin habilitar.
Informe informe-etapa-4.md y evidencia docs/evidence/eft. BFF existentes no modificados: necesitan recargar certificado previo antes de su próxima validación HTTPS.
Próxima etapa: recargar TLS/verificar BFF legacy y definir integración moderna; sin cloud/merge/push/escalado en Etapa4.

## Cierre Etapas 5 y 6

Etapa 5: BFF Web/Mobile/ATM recargaron TLS vigente, contratos 200, OAuth 401/403 y Mobile 200→503→200; verify 245. Informe informe-etapa-5-bff-tls.md.

Etapa 6: Customer/Account/Payment 2+2+2 real con Eureka/IP únicos, HTTPS por identidad de servicio, PostgreSQL compartido, claims/lease outbox, routing y Kafka group compartido 2 consumidores/3 particiones/lag 0. Fallo real de caché corregido con cache LoadBalancer deshabilitada en override y Spring Retry idempotente Payment.

Run eft6-360e45c45f52: 25 postings/operaciones COMPLETED/outboxes PUBLISHED/audits, sin duplicados lógicos; saldos 101/20. Failover individual de tres servicios y recuperación PENDING con mismo operationId/posting único. Filas previas de once tablas, volumen e infraestructura preservados.

Retorno al Compose base 1+1+1 saludable; BFF singleton con respuestas idénticas y Account host 8085 verificado. Focalizados 199 y verify final 256 reportados sin fallos/errores/skipped; seis tests PostgreSQL reales habilitados. KafkaReal heredados condicionados; broker Docker probado por runner.

Informe informe-etapa-6-escalabilidad.md, auditoría auditoria-etapa-6-escalabilidad.md y procedimiento despliegue.md. Sin push/merge/cloud/borrado de volúmenes. Próximo alcance: preparación cloud real con PKI/secretos/migraciones/observabilidad antes de desplegar; integración BFF moderna/titularidad separadas.

## Cierre documental Etapa 7

Inventario completo, matriz C1–C8 sin puntajes inventados, readme.md/instrucciones.md/despliegue.md raíz, borrador de 23 secciones, tres diagramas Mermaid, selección de 20 evidencias y guion ~6 min. README Semana 8 preservado íntegro como README-semana-8.md por colisión de mayúsculas/minúsculas en Windows. Contratos actualizados a publisher Etapa 6 sin modificar APIs/código/configuración.

mvn verify vía helper: BUILD SUCCESS, 256 tests actuales, 0 fallos/errores/skipped; seis PostgreSQL habilitados. Evidencia etapa7-01-mvn-verify.txt. No repetición de 2+2+2 ni detenciones funcionales. Sin push/merge/PDF/cloud. Ese cierre se corrige en Etapa 7.1: pauta/puntajes conocidos; la plantilla oficial ya está identificada y disponible según actualización vigente; revisar el PDF ya generado y su DOCX editable. El laboratorio cloud se ejecutó posteriormente según el cierre AWS siguiente. El PDF/DOCX ya está generado; video y cierre/publicación autorizado pendientes. El dataset conserva su condición de reproducción documentada.

## Corrección documental Etapa 7.1

Pauta e instrucciones (forma A) leídas: C1–C8 = 10/15/15/15/15/10/10/10, máximo 100. C1: Batch a Spring Batch, división monolito en microservicios, BFF, seguridad distribuida Spring Cloud Security y Kafka. C6 local completo, cloud ejecutado y documentado en AWS EC2 según actualización vigente. C7 entregables preparados/parciales y PDF final generado y listo para revisión, con versión DOCX editable; plantilla oficial identificada y disponible según actualización vigente; C8 guion conocido, video MP4 5–7 min con webcam/evidencias pendiente. Cuatro componentes y video juntos en una carpeta. Corrección de fences Mermaid segundo y tercero. Sin código/configuración, escalado, cloud, PDF, push ni merge.

## Incorporación de evidencia AWS EC2

Capturas revisadas el 8 de octubre de 2026: [despliegue base](../evidence/eft/capturas/1_despliegue_contenedores_nube.png), [smoke](../evidence/eft/capturas/3_smoke_test_funcional_200_201_201.png), [2+2+2](../evidence/eft/capturas/aws-escalabilidad-horizontal-2x2x2.png), [Eureka](../evidence/eft/capturas/aws-eureka-2x2x2-up.png), tres failovers y recuperación (índice). Ejecución cloud ya completada; usuario confirma retorno final 1+1+1, sin captura consolidada de ese cierre. Las capturas HTTP usan curl -k y no acreditan verificación estricta TLS AWS. Solo se actualizó documentación; sin ejecución de despliegue, código/configuración o cambios a imágenes. Siguiente paso: revisar el PDF/DOCX generado, grabar el video y completar el paquete y publicar únicamente cuando se autorice.
