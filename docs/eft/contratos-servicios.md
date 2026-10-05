> Contrato vigente actualizado al cierre de Etapa 6. Etapas 4–6 validaron runtime/TLS y coordinación horizontal sin cambiar las APIs. El título Etapa 2 conserva procedencia histórica.

# EFT Etapa 2 — contratos de servicios

Maestros registrales y operaciones financieras implementados y validados. Las propuestas iniciales se conservan en el modelo de dominio histórico; este documento describe las APIs vigentes.
JSON, Authorization: Bearer JWT válido (issuer/audience/firma/tiempo + roles), scopes de operación.
Errores JSON de dominio: {"code":"...","message":"..."}; OAuth2 401/403 conserva mecanismo Resource Server.
Nunca interpretar customerId o sub técnico como autenticación de cliente final.
Rutas de Semana 8 /internal/accounts/* se mantienen intactas con ROLE_WEB/MOBILE/ATM.

## Tipos

Customer: {customerId: UUID, name: string, version: long}.
Account: {accountId: long, accountType: ahorro|prestamo, status: OPEN|CLOSED, version: long, customerIds: UUID[]}.
No balance en el contrato registral. name <=120 caracteres, no blanco; no RUT/contactos/edad inferidos.
Versión inicia 0. Mantenimiento/cierre exige versión >=0.
Titulares explícitos, al menos uno, UUIDs no nulos y sin repetidos; no inferir por nombres.

## Account Service — implementado

| Método / URI | Request | Response y HTTP | Validaciones | Responsable/autorización | Efectos persistentes | Evento |
|---|---|---|---|---|---|---|
| PUT /api/accounts/{accountId} | {accountType,customerIds} | Account; 201 nuevo con Location; 200 replay idéntico; 400 entrada inválida; 404 cliente inexistente; 409 clave distinta/legacy pendiente; 401/403; 503 dependencia/BD | accountId positivo; tipos soportados; IDs únicos; validar Customer; rechazo clave usada en legacy | Account; accounts.write y customers.read | eft_account OPEN (API ACTIVE)/version0, vínculos y eft_account_balance=0, una transacción | ninguno |
| GET /api/accounts/{accountId} | sin body | Account 200; 400 ID; 404 ausente; 401/403; 503 BD | cuenta maestra existente | Account; accounts.read | ninguno | ninguno |
| PATCH /api/accounts/{accountId} | {accountType,version} | Account 200; 400 inválido; 404 ausente; 409 versión/estado cerrado; 401/403; 503 BD | tipo permitido, versión actual, ACTIVE (OPEN físico) | Account; accounts.write | tipo y version+1 por compare-and-set; sin cambiar titulares/saldos | ninguno |
| POST /api/accounts/{accountId}/closure | {version} | Account 200; 400; 404; 409 versión/estado; 401/403; 503 BD | versión actual; repetición con versión anterior al cierre devuelve mismo CLOSED | Account; accounts.write | ACTIVE→CLOSED/version+1 (OPEN físico); retiene saldo, no liquidación | ninguno |
| GET /api/accounts?customerId={UUID}&limit={1..100}&offset={>=0} | query; defaults limit20/offset0 | Account[] 200; 400 query; 401/403; 503 BD | UUID obligatorio, paginación; no valida existencia cliente en esta ruta interna | Account; accounts.read | SELECT vínculos; orden ID | ninguno |

Si accountId existe en salida legacy, PUT 409 LEGACY_MIGRATION_REQUIRED; no apropiarse de titulares históricos contradictorios.
Account asigna saldo operacional propio 0; no agrega moneda. OPEN físico se representa como ACTIVE en la API.
Estas cuentas operan mediante Payment; la integración de los BFF/ATM legacy queda para una etapa posterior.

## Customer Service — implementado

| Método / URI | Request | Response y HTTP | Validaciones | Responsable/autorización | Efectos persistentes | Evento |
|---|---|---|---|---|---|---|
| PUT /api/customers/{customerId} | {name} | Customer; 201/Location nuevo; 200 mismo ID/name; 400 formato; 409 mismo ID/name distinto; 401/403; 503 BD | UUID, name no blanco <=120, trim exterior; nombre no único | Customer; customers.write | eft_customer/version0; sin carga legacy | ninguno |
| GET /api/customers/{customerId} | sin body | Customer 200; 400 UUID; 404 ausente; 401/403; 503 BD | existe registro explícito | Customer; customers.read | ninguno | ninguno |
| PATCH /api/customers/{customerId} | {name,version} | Customer 200; 400; 404; 409 versión; 401/403; 503 BD | mismo name válido, versión vigente | Customer; customers.write | name/version+1; sin cambiar identidad ni vínculos | ninguno |
| GET /api/customers/{customerId}/accounts?limit=&offset= | defaults20/0 | Account[] 200; 400; 404 cliente; 401/403; 503 Account/BD | primero existe cliente; paginación válida | Customer; customers.read y accounts.read | consulta remota Account con JWT original | ninguno |

La consulta Customer→Account devuelve una proyección registral {accountId,accountType,status,version,customerIds}; el balance y customerId singular se consultan directamente en Account.
No endpoint que adivine cliente por nombre ni que elimine identidad/vínculos.
No esconder caída Account como []: una lista vacía solo significa respuesta real de Account sin asociaciones.

## Account — API financiera interna implementada

Account completo: {accountId,accountType,status,version,customerIds,customerId,balance}.
customerId contiene el UUID cuando existe un solo titular; null si hay varios, que permanecen explícitos en customerIds.
Estado externo ACTIVE/CLOSED, saldo decimal con hasta 17 enteros y 2 decimales; version aumenta en cada movimiento.
El cierre conserva registro, titulares y saldo incluso cuando es distinto de cero; no liquida fondos ni permite reapertura.

| Método / URI | Request | Response / HTTP / errores | Scopes | Persistencia | Kafka |
|---|---|---|---|---|---|
| POST /internal/accounts/postings | Idempotency-Key; {operationId UUID,type DEPOSIT/PAYMENT/TRANSFER,sourceAccountId nullable,targetAccountId nullable,amount} | FinancialOperationResult; 201 nuevo/200 replay; 400 INVALID_REQUEST; 404 ACCOUNT_NOT_FOUND; 409 ACCOUNT_CLOSED, INSUFFICIENT_BALANCE, BALANCE_CAPACITY_EXCEEDED, IDEMPOTENCY_CONFLICT, OPERATION_ID_CONFLICT; 401/403; 503 DATABASE_UNAVAILABLE | accounts.post | Bloqueo de cuentas en orden ID; saldo(s), versiones, comprobante eft_account_posting y outbox en una transacción Account | Evento completado en outbox, publicación asíncrona |
| GET /internal/accounts/postings/{operationId} | sin body | FinancialOperationResult 200; 400 UUID; 404 OPERATION_NOT_FOUND (también otro actor); 401/403; 503 BD | accounts.post.read | SELECT comprobante del sub verificado | ninguno |
| GET /internal/accounts/{accountId}/operational-balance | sin body | Account completo 200; 400 ID; 404 ACCOUNT_NOT_FOUND; 401/403; 503 BD | accounts.read | SELECT maestro y saldo operacional | ninguno |

DEPOSIT exige origen null/destino positivo; PAYMENT origen positivo/destino null; TRANSFER dos IDs positivos distintos.
amount > 0 y representación DECIMAL(19,2). La capacidad decimal es restricción de almacenamiento, no límite financiero regulatorio.
FinancialOperationResult: {operationId,type,sourceAccountId,targetAccountId,amount,status:"COMPLETED",sourceBalanceAfter,targetBalanceAfter,completedAt}.
El saldo posterior de una cuenta no involucrada es null. El replay devuelve el comprobante original aunque haya movimientos posteriores.

## Payment Service — implementado

Todos los POST exigen Idempotency-Key de 1..120 caracteres [A-Za-z0-9._:-]. Identidad: sub del JWT verificado.
Hash SHA-256 del tipo, cuentas e importe normalizado; 10 y 10.00 son equivalentes. operationId se genera y persiste antes de llamar a Account.
Misma clave y actor con distinto payload produce 409; distinto actor tiene espacio de claves independiente.

PaymentResponse: {operationId,type,sourceAccountId,targetAccountId,amount,status,createdAt,result,failureCode}.
result contiene el comprobante Account al completar; null en PENDING/FAILED. completedAt está dentro de result.
Error controlado: {code,message}; nunca un comprobante exitoso inventado.

| Método / URI | Request | Response / HTTP / errores | Scopes | Persistencia | Kafka |
|---|---|---|---|---|---|
| POST /api/payments/deposits | {accountId,amount} + Idempotency-Key | PaymentResponse; 201 primer éxito/200 replay o recuperación; 400 validación; 404 cuenta; 409 idempotencia/cuenta cerrada/capacidad; 401/403; 503 Account/BD | payments.write; JWT relayed requiere accounts.post en Account | eft_payment_operation PENDING antes de HTTP; crédito atómico Account; COMPLETED con comprobante; rechazo determinista FAILED | DepositCompleted desde Account |
| POST /api/payments/transfers | {sourceAccountId,targetAccountId,amount} + clave | mismo contrato; además 409 INSUFFICIENT_BALANCE, 400 origen=destino | payments.write y accounts.post remoto | Débito+crédito indivisibles en Account; Payment registra estado local fuera de la transacción remota | TransferCompleted |
| POST /api/payments | {sourceAccountId,amount} + clave | mismo contrato; 409 saldo insuficiente/cerrada | payments.write y accounts.post remoto | Débito registrado sin integración a comercio, adquirente ni beneficiario externo | PaymentCompleted |
| GET /api/payments/operations/{operationId} | sin body | PaymentResponse 200 incluyendo PENDING/FAILED reales; 400 UUID; 404 OPERATION_NOT_FOUND (también otro actor); 401/403; 503 BD | payments.read | SELECT operación del sub verificado | ninguno |

400/404/409 recibidos de Account se conservan como FAILED para replay estable. Fallo de red, timeout, circuito o 5xx deja PENDING y devuelve 503 ACCOUNT_UNAVAILABLE.
401/403 remoto devuelve 403 DEPENDENCY_FORBIDDEN y deja PENDING. Se puede recuperar con la misma clave y autorización válida.
GET muestra estado; para recuperar activamente se repite el POST con misma clave/payload. No existe transacción ACID entre Payment y Account.
Account ya confirmado puede coexistir temporalmente con Payment PENDING; reintento idempotente o evento validado reconcilia sin mover fondos otra vez.
La clave se retiene indefinidamente en esta etapa; no hay política automática de expiración.

## Kafka financiero

Topic banco.operaciones.completadas.v1, key operationId, tres particiones, réplica1 local.
JSON: {eventId UUID,version:1,actor,requestHash,result:FinancialOperationResult}.
Tipo semántico derivado de result.type: DepositCompleted / TransferCompleted / PaymentCompleted.
No incluye JWT, claves OAuth ni PII inventada. actor es cliente técnico.

eft_financial_outbox se inserta en la misma transacción de saldo/comprobante.
Publicador Account reclama una fila mediante PostgreSQL FOR UPDATE SKIP LOCKED y UPDATE RETURNING. Estado PROCESSING, owner/token y lease de 30s; recupera claims vencidos. Base: hasta 20 claims por ciclo cada 2s; escala: uno cada 300ms. Espera ack hasta 5s; solo ownership/lease vigente tras ack permite PUBLISHED/published_at.
Error devuelve el claim propio a PENDING con retry diferido de 2s. No mantiene la transacción DB abierta durante Kafka. Un token obsoleto no puede confirmar un claim recuperado.
Entrega al menos una vez: ack perdido o caída antes del update permite duplicados con mismo eventId; deduplicación Payment mantiene auditoría lógica única. No garantiza exactly-once físico.
Dos publishers simultáneos validados en Etapa 6. Véase [informe de escala](informe-etapa-6-escalabilidad.md).

Payment consume con grupo financial-payment-audit: valida estructura, hash, comprobante y correlación actor/request con operación existente.
Persiste auditoría eft_payment_event_audit deduplicada por eventId y reconcilia PENDING dentro de una transacción local.
Eventos de comandos Account sin operación Payment se auditan sin inventar un pago.
No aplica saldos, no duplica Anomaly Service. Inconsistencia revierte auditoría/proyección.
Errores transitorios: 2 reintentos cada1s; mensajes malformados o inválidos pasan a banco.operaciones.completadas.v1.DLT.
El publicador de DLT admite bytes originales; fallo al publicar DLT impide considerar recuperado el mensaje.

## Compatibilidad y acceso técnico

Scopes nuevos y separados de accounts.web/accounts.mobile/accounts.atm. Cliente técnico opcional banco-domain-operator con secreto externalizado y ROLE_DOMAIN_OPERATOR; no es usuario final.
Roles WEB/MOBILE/ATM solos reciben 403 en contratos nuevos. El operador no obtiene roles de canal ni permisos Payment.
PUT idempotente por identidad/payload no equivale a idempotencia financiera general; PATCH usa versión. Customer devuelve name vigente, no snapshot histórico.
Endpoints de salud conservan acceso sin autenticación; otras rutas Actuator permanecen cerradas.

## HTTP de dependencias

Customer desconocido: Account expone 404 CUSTOMER_NOT_FOUND, sin escribir.
401/403 remotos: rechazo 403 DEPENDENCY_FORBIDDEN, sin revelar credenciales.
Error de conexión/5xx/circuito abierto: 503 CUSTOMER_UNAVAILABLE o ACCOUNT_UNAVAILABLE, sin datos ficticios.
BD: 503 DATABASE_UNAVAILABLE; integridad/carrera: 409 CONFLICT. Errores de formato/Bean Validation: 400 INVALID_REQUEST.
No exponer SQL/stack traces ni mensajes arbitrarios de la dependencia.
## Seguridad y despliegue Etapa 3

Cliente opcional banco-payment-operator / ROLE_PAYMENT_OPERATOR: payments.write, payments.read, accounts.post, accounts.post.read.
Se habilita con OAUTH_PAYMENT_CLIENT_SECRET generado por initialize-compose-environment.ps1; sin secreto permanece deshabilitado.
banco-domain-operator conserva accounts.read/accounts.write/customers.read/customers.write, sin ejecución financiera.
OAuth client_credentials autentica clientes técnicos, no personas; titularidad explícita no implica autorización IAM de usuario final.
JWT RSA verificado con issuer, audience, expiración y roles; HTTPS y truststore del entorno Compose. Sin token401, scope incorrecto403.
Payment→Account: circuito accountPosting (3 llamadas mínimas, ventana4, umbral50%, apertura10s, una prueba HALF_OPEN), connect2s/read3s, timelimiter5s. Errores4xx no abren circuito.
Account→Customer conserva customerRegistry; ante indisponibilidad no abre cuentas huérfanas.
Account/Customer/Payment integrados en Compose base. Customer8087 y Payment8086 solo internos; Account conserva host8085 de Semana8.
No se altera el contrato legacy de los tres BFF ni el flujo Batch/anomalías.
