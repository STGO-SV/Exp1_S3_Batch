> Documento histórico de trazabilidad interna. Estado final (2026-10-10): desarrollo técnico completo; EFT integrada en main y publicada en GitHub. Despliegue en entorno AWS EC2 ejecutado y documentado; capturas finales incorporadas. Informe PDF generado con la plantilla oficial y DOCX editable disponible. Video grabado y entregado fuera del repositorio, sin enlace alojado en este árbol.

> Lectura final: las propuestas de Etapas 1/2 son históricas. El dominio operacional se consolidó en Etapa 3; validación real y coordinación de publishers se acreditan en Etapas 4–6. APIs vigentes: [contratos-servicios.md](contratos-servicios.md); síntesis: [borrador técnico](borrador-informe-tecnico.md).

> Documento evolutivo: Etapas 1/2 describen estado histórico. Las decisiones vigentes de Etapa 3 al final sustituyen las propuestas pendientes de saldo y Payment.

# EFT Etapa 2 — modelo de dominio mínimo

Estado: diseño documentado antes de implementar. Base eft 9c19005; main 76b9773.
Fecha: 4 de octubre de 2026. Los términos propuesto, implementado y pendiente se distinguen deliberadamente.

## Modelo real inspeccionado

Fuentes: todos los records de core/Batch, repositories JDBC, processors/writers, schema.sql Batch/Anomaly, SQL explain, DTO/controladores BFF/Account y los nueve CSV completos de semanas 1–3. Se intentó inspección READ ONLY del PostgreSQL de Compose: Docker Desktop no está activo (pipe del motor Linux ausente). Por tanto el esquema efectivo de una instancia viva no está verificado; se describe el SQL versionado.

| Concepto | Identidad real | Atributos y naturaleza | Relación comprobable |
|---|---|---|---|
| Cuenta candidata | cuenta_id (Long/BIGINT), no id autogenerado de la fila | nombre, saldo, edad, tipo en intereses.csv | misma clave numérica en cuentas_anuales.csv; no FK ni maestro |
| Interés procesado | id de fila generado, cuenta_id repetible | nombre, saldo_original, tasa, saldo_procesado, tipo | derivado del CSV y reglas ahorro 1% / préstamo 2% existentes; sin periodo |
| Movimiento anual | id de fila generado; CSV sin ID del movimiento | cuenta_id, fecha, tipo, monto, descripción | referencia por cuenta_id sin FK; histórico, salvo retiros ATM insertados |
| Transacción diaria | id CSV → transaccion_id; id SQL es otro identificador | fecha, monto, débito/crédito, anomalía derivada | SIN cuenta_id: no unir a cuenta o titular por igualdad numérica |
| Titular/cliente | NO existe identificador | nombre; edad solo en entrada Batch | no hay relación inequívoca persona-cuenta |
| Anomalía/outbox | event_id UUID, transaction_id | importe/tipo/fecha, motivo, correlación, estado de publicación | transaction_id vincula evento a transacción; no a cuenta |
| Anomalía consumida | event_id PK y transaction_id UNIQUE | payload + tópico/partición/offset/instancia | dedup protege más que solo UUID; no representa identidad cliente |
| Estado de cuenta | NO entidad propia | movimientos anuales procesados | el job no genera un resumen/documento anual ni tiene periodo persistido |

Inspección de CSV completa, sin publicar nombres personales:
- Semana 1: 8 filas de intereses/8 claves, 9 movimientos, 10 transacciones; 1 nombre aparece en varias claves.
- Semana 2: mismos totales; 1 nombre compartido entre claves.
- Semana 3: 1000 filas de cada archivo; 50 claves de cuenta en intereses. Las 50 claves contienen nombres distintos entre filas; 8 nombres aparecen en varias claves. Los movimientos tienen referencias presentes entre esas claves, pero eso no prueba titularidad.
- Semana 3 contiene ahorro/prestamo/hipoteca/unknown/-1; el processor admite únicamente ahorro/prestamo.
- Movimientos incluyen compra/deposito/retiro y también pago/depósito en Semana 3; el processor admite únicamente compra/deposito/retiro. Presencia en CSV no prueba procesamiento ni ejecución de pago.
- Transacciones válidas son debito/credito; invalid/desconocido y datos malformados forman parte de los casos de rechazo.
- No hay moneda, comercio, beneficiario, cuenta origen/destino de transferencia, ID de cliente, RUT, domicilio, teléfono, email, fecha de nacimiento ni identidad de usuario final.

No se puede deducir una cuenta con varios titulares de los nombres contradictorios: podrían ser corrupción, datos sintéticos o cambios históricos. Tampoco se pueden fusionar nombres repetidos como una persona.

## Fuente de verdad actual y riesgo financiero

LegacyAccountReadRepository obtiene la última fila por id DESC de interes_procesado para representar saldo/titular/tipo. No hay fecha/periodo de interés en esa tabla; id es orden de inserción, no necesariamente cronología de negocio. Procesamiento paralelo y reejecuciones complican esa suposición.

AtmWithdrawalService bloquea esa fila con FOR UPDATE y actualiza saldo_procesado, insertando retiro en movimiento_anual_procesado en la misma transacción. Así una salida derivada Batch se volvió estado financiero mutable; una nueva fila Batch puede desplazar el saldo modificado. Los movimientos por sí solos no permiten reconstruir saldo: no se conoce base temporal, y algunos importes negativos se admiten sin semántica uniforme.

No se copiarán automáticamente filas a un maestro, ni se escogerá arbitrariamente un saldo o titular. El saldo legacy sigue funcionando para Semana 8; esa compatibilidad NO equivale a resolver la migración financiera.

## Modelo objetivo mínimo implementable sin inventar finanzas

Se implementará un registro maestro administrativo separado, sin columna de saldo ni ledger en esta etapa.

| Tabla nueva | Campo | Justificación |
|---|---|---|
| eft_customer | customer_id UUID PK | Identidad técnica explícita del registro, aportada por operador; no RUT ni identidad inferida |
| eft_customer | name VARCHAR(120) | Único atributo de persona justificable por dataset y esquema; no es UNIQUE |
| eft_customer | version BIGINT | Concurrencia optimista y conflictos de actualización |
| eft_account | account_id BIGINT PK | Mismo tipo de identificador que cuenta_id; valor explícito, no id de fila Batch |
| eft_account | account_type VARCHAR(20) | clasificación ahorro/prestamo ya soportada; no calcula tasa/comisión |
| eft_account | status VARCHAR(20) | ciclo administrativo OPEN/CLOSED requerido; no afirma liquidación financiera |
| eft_account | version BIGINT | protección ante mantenimiento/cierre concurrente |
| eft_account_holder | account_id, customer_id PK compuesta | vínculos explícitos suministrados por operador; ningún vínculo se infiere de CSV |

Solo hay FK local holder → eft_account. No hay FK a tablas de otro servicio. Customer no implementa eliminación: una identidad comprobada no desaparece entre validación remota y asociación. Una consulta remota valida existencia, no identidad humana ni veracidad de la declaración de titularidad.

La relación permite múltiples vínculos explícitos sin afirmar que los CSV prueban cotitularidad. El registro puede responder titular(es) y cuentas declaradas de un cliente. Crear clientes no carga nombres desde legacy. Sus UUID no son sujetos OAuth2.

### Alcance exacto de Account

- Apertura: crear una cuenta administrativa con tipo y titulares explícitos comprobados en Customer.
- Mantenimiento: cambiar clasificación administrativa con versión; no recalcular intereses ni alterar saldos.
- Cierre: cerrar ese registro, bloqueando mantenimiento posterior; no liquidar fondos ni cerrar cuentas legacy en ATM.
- Consulta: metadatos del registro y vínculos.
- Apertura no puede reutilizar una clave ya presente en intereses/movimientos legacy: 409 LEGACY_MIGRATION_REQUIRED. Reusar la clave durante una futura migración requiere selección validada de titular/snapshot.
- No saldo inicial inventado, movimiento de apertura, políticas de comisiones, moneda ni reglas de cierre financiero.
- Los contratos BFF internos de Semana 8 siguen usando legacy; no se promete operación financiera de las nuevas cuentas ni autorización por propietario.
- Esta implementación completa un ciclo registral mínimo, NO Gestión de Cuentas financiera integral. Migración de saldo y efecto del cierre sobre ATM se detienen para revisión humana.

El guard de apertura consulta los datos legacy actuales; no puede impedir que un CSV futuro introduzca la misma clave. Reservar/asignar claves y política de importación forma parte de la migración pendiente.

## Consistencia, persistencia y concurrencia

SQL aditivo reproducible por servicio: CREATE TABLE IF NOT EXISTS, checks, PK e índice holder(customer_id,account_id). Inicialización al arrancar Account/Customer, compatible PostgreSQL y H2; no depende de vaciar volúmenes Docker. No DROP, ALTER ni transformación de tablas históricas.

Account posee su maestro/vínculos; Customer posee registros/name. Compartir servidor PostgreSQL local no permite que Customer acceda a tablas Account. La apertura valida clientes fuera de la transacción DB; inserta maestro y todos los vínculos dentro de una sola transacción. Fallos no dejan cuenta parcial. PK preserva unicidad ante carrera; replay de PUT idéntico devuelve 200 sin duplicar vínculos; contenido distinto para la misma clave da 409.

Mantenimiento y cierre hacen UPDATE WHERE version=?; una sola fila afectada o 409. No hay borrado ni cambio de titulares. Customer usa la misma política de PUT por UUID y PATCH con versión. Listado de cuentas por cliente paginado con limit/offset, orden por account_id.

Consistencia fuerte de cada comando local. Validación de existencia remota + ausencia de borrado basta para la referencia en esta etapa; nombre puede cambiar independientemente. Los vínculos son declaraciones operativas, no certificaciones de identidad.

## Payment: diseño condicionado, sin implementación financiera

Interpretable académicamente:
- Depósito: crédito a una cuenta con movimiento.
- Pago: débito registrado a una cuenta; compra/debito/pago en datos son evidencia de tipos, no un comercio ni proveedor externo.
- Transferencia: débito a origen y crédito a destino; el dataset no posee esa relación ni identifica orden de transferencia.

Se propone la interpretación de pago como débito académico, NO se adopta una operación ejecutable mientras falten saldo operativo/ledger y política del cierre. Reutilizable: validación decimal positiva, saldo suficiente, bloqueo y rollback del retiro ATM. No trasladarlo sin resolver la fuente de saldo.

Diseño objetivo: Account será propietario de ambos saldos y del posting financiero. Payment recibe una orden, delega un comando atómico a Account y devuelve su resultado real. Débito/crédito, movimientos, comprobante idempotente y outbox se confirmarían juntos en la misma transacción Account. Bloqueo de cuentas en orden por ID evita deadlocks; rechazo si destino/origen inválidos. No ejecutar dos updates remotos independientes, ni crédito asíncrono por Kafka como si una transferencia fuera atómica.

Payment no mantiene un segundo comprobante obligatorio en otra transacción como fuente de verdad; Account guarda resultado consultable. Idempotency-Key + identidad técnica + operación con hash canónico del request: mismo payload devuelve resultado original, distinto da 409. Timeout produce resultado incierto/503 y requiere reconciliación por clave; nunca emitir éxito ni reintentar con una clave nueva.

Semántica de importe: DECIMAL(19,2) y restricciones representacionales existentes, sin denominarlo CLP ni añadir moneda/limites de negocio/comisiones. No reconstruir ledger de legacy por signos de CSV. Tests de saldo insuficiente/transferencia/rollback financiero quedan pendientes porque no hay esa lógica nueva.

## Eventos útiles y outbox

No se emiten eventos nuevos desde Account/Customer registrales: todavía no existe consumidor con función aprobada. Ni AccountOpened/Closed ni CustomerUpdated se agregan solo para cumplir criterio Kafka.

Propuestos tras migración:
- DepositCompleted/PaymentCompleted/TransferCompleted: actualizar proyección de movimientos para canales y reconciliar estado de orden; no aplicar de nuevo el saldo.
- CustomerUpdated: solo si se adopta una proyección de presentación; ahora Account guarda UUID y consulta a Customer, no almacena copia del nombre.
- AccountClosed: solo cuando un consumidor necesite invalidar una proyección operacional.

Reusar el patrón outbox, no la tabla anomaly_event_outbox: esta contiene campos específicos de transacciones anómalas. Futuro outbox de dominio separado con event_id/type/version/aggregate_id/payload/correlation/status; contrato versionado y claim/lease antes de publishers múltiples. No tocar flujo anomaly/retry/DLT. Consumer actual también tiene UNIQUE(transaction_id), no afirmar dedup exclusivamente por UUID.

## Autorización

WEB/MOBILE/ATM son autorización de canal técnico. No identifican clientes finales y no bastan para administrar maestros.
Se requieren scopes accounts.read/accounts.write/customers.read/customers.write para operación administrativa explícita. Un cliente técnico opcional banco-domain-operator (ROLE_DOMAIN_OPERATOR) obtiene estos scopes si se configura su secreto externo. No entrega identidad de titular ni permisos a BFF existentes.
Account → Customer reenvía el JWT original y requiere customers.read además de accounts.write para apertura.
Customer → Account reenvía JWT y necesita customers.read + accounts.read para listar cuentas.
No conceder permisos por nombre ni por UUID aportado en request. Titularidad de usuario final requiere fuente de identidad y claim/vínculo validados posteriormente.

## Resilience4j real

| Llamante → llamado | Función | Error esperado | Fallback |
|---|---|---|---|
| Account → Customer | Comprobar cada customer_id antes de apertura | 404 cliente; 401/403; timeout/5xx/circuito abierto | 404 explícito; 403 de permiso sin degradarlo a éxito; indisponibilidad 503 sin guardar cuenta |
| Customer → Account | Leer cuentas declaradas para cliente existente | 401/403; timeout/5xx/circuito abierto | 403 o 503; jamás devolver lista vacía falsa |
| Payment → Account (propuesto) | Posting atómico financiero | timeout/5xx y resultado incierto | 503/consulta por clave, sin saldo ni comprobante inventado |

Time-outs 2s/3s y circuit breaker. No reintentos automáticos de escritura. No llamada Payment → Customer solo para marcar una casilla.

## Revisión humana necesaria

1. Qué fila/periodo determina saldo inicial legacy y cómo retirar mutable estado de interes_procesado.
2. Qué nombres/identidades son titulares verdaderos ante contradicciones de Semana 3.
3. Asignación/reserva de account_id y importaciones futuras.
4. Alcance financiero de apertura/mantenimiento/cierre, préstamos y efecto sobre ATM.
5. Aceptar interpretación académica de pago y ledger/estados/reconciliación; moneda si se exige.
6. Identidad de usuarios finales y autorización por titularidad.
7. AWS conserva su seguimiento en los documentos de despliegue; la plantilla oficial PBY2203_EFT_S9_plantilla_PDF.docx ya está identificada y disponible. El PDF final y el DOCX editable ya están generados con la plantilla oficial. El video está grabado y se entrega fuera del repositorio; main está publicada.

No se desplegará, escalará ni modificará destructivamente el esquema en esta etapa.
## Etapa 3 — decisiones aprobadas y ejecución operacional

Las decisiones financieras anteriormente pendientes quedan resueltas para cuentas maestras nuevas: saldo inicial 0, estado ACTIVE/CLOSED, importes positivos, débito sin sobregiro y pago como débito registrado sin comercio externo. No se migran saldos ni titulares legacy.

Se añade eft_account_balance (account_id PK/FK local, balance DECIMAL(19,2) >=0). La versión existente de eft_account se incrementa en cada movimiento. Para preservar el esquema registral sin alterar registros/constraints, OPEN persistido se presenta como ACTIVE en la API; CLOSED conserva su significado. Los registros de Etapa 2, que no tenían saldo ni movimientos financieros, reciben saldo0 mediante INSERT de filas faltantes; jamás desde resultados Batch. No se elimina ni transforma ninguna tabla legacy. customerIds/vínculos explícitos existentes se conservan; customerId singular se presenta cuando existe un único titular.

Account recibe posting idempotente (DEPOSIT/PAYMENT/TRANSFER), bloquea las cuentas en orden de ID y confirma en una sola transacción: saldo(s), versiones, comprobante y evento outbox. No hay ACID distribuida. Cierre usa la versión y bloquea operaciones posteriores, conserva saldo/registro y no reabre; no se agrega una regla no aprobada que fuerce liquidación o saldo cero.

Payment persiste PENDING antes de llamar a Account. 404/409 de negocio se registran FAILED; timeout/conexión/circuito abierto permanecen PENDING porque Account podría haber confirmado. Misma identidad técnica+Idempotency-Key+payload canónico conserva operationId; payload distinto da409. Retry con misma clave o evento Kafka completado reconcilian el resultado; nunca asumir éxito en fallback. COMPLETED se registra solo con comprobante válido o evento validado.

Outbox financiera específica de Account, separada de anomaly_event_outbox. Eventos DepositCompleted/TransferCompleted/PaymentCompleted contienen comprobante y correlación técnica. Payment consume para historial auditable y reconciliación de PENDING; no aplica saldos. Dedup por event_id, resultado inmutable y correlación actor/request_hash. Consumer retry/DLT; publisher conserva PENDING si Kafka falla. Entrega al menos una vez, no exactly-once ni atomicidad entre brokers/servicios.

Scopes de Etapa 2 se conservan. Nuevo cliente técnico opcional banco-payment-operator: payments.write/read y accounts.post/post.read; sin administración Customer/Account ni roles de canal. Domain operator mantiene sus cuatro scopes administrativos. JWT original se propaga Payment→Account; actor se obtiene del sub verificado, no de un header libre. No IAM de personas.

Se mantienen cuentas modernas fuera de BFF/ATM legacy hasta integrar explícitamente esos canales en etapa posterior. Cuentas CLOSED rechazan todas las operaciones financieras modernas; un saldo positivo permanece registrado, sin liquidación inventada.

Payment almacena tipo/cuentas/amount dentro de request JSON inmutable, y completedAt/saldos posteriores dentro de receipt JSON; estado, actor, clave, hash, createdAt y failureCode/status están en columnas. No se añade PII.
Account guarda comprobante JSON inmutable y outbox propia; eft_account_balance protege balance>=0.
Titularidad múltiple de Etapa2 conservada: customerId singular solamente cuando hay un titular. Customer sigue UUID/name/version sin eliminación física.
