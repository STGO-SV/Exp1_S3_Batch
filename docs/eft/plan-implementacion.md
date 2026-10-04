# EFT — plan de implementación

Base: 76b9773. Rama: eft. Mantener commits pequeños, sin push/merge hasta revisión.
AWS y plantilla PDF no se presuponen. No producir aún PDF/video.

## Fase 1 — auditoría y base técnica (esta etapa)

- Matriz trazable, cinco procesos críticos propuestos y brechas por dominio.
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
- Esperar plantilla PDF o aclaración del docente para formato final.
- Informe PDF y video solo después de completar negocio, pruebas y confirmar formato.

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

Validado Compose/PostgreSQL16.4/HTTPS con certificado verificado/OAuth/Customer/Account/Payment/Kafka real.
Imagen Account antigua corregida reconstruyéndola; SQL empaquetado y ocho tablas EFT inicializadas por servicios.
Credencial financiera completada; secretos/JWT previos preservados. TLS regenerado solo porque faltaban SAN Customer/Payment.
Flujos financieros, idempotencia, cierre/rollback,503/PENDING/recuperación HTTP y reconciliación Kafka ejecutados. Cinco postings/outboxes PUBLISHED/audits, sin duplicación.
DLT real y continuidad después de mensaje inválido confirmadas; retries transitorios de infraestructura cubiertos por tests, sin fallo forzado de DB/Kafka compartidos.
mvn verify final BUILD SUCCESS:245 tests reportados,0 fallos/errores; cuatro KafkaReal heredados sin habilitar.
Informe informe-etapa-4.md y evidencia docs/evidence/eft. BFF existentes no modificados: necesitan recargar certificado previo antes de su próxima validación HTTPS.
Próxima etapa: recargar TLS/verificar BFF legacy y definir integración moderna; sin cloud/merge/push/escalado en Etapa4.
