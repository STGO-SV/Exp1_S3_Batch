# EFT Etapa 2 — contratos de servicios

Diseño previo a implementación. Maestros registrales nuevos; operaciones financieras Payment solo propuestas.
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

## Account Service — implementable

| Método / URI | Request | Response y HTTP | Validaciones | Responsable/autorización | Efectos persistentes | Evento |
|---|---|---|---|---|---|---|
| PUT /api/accounts/{accountId} | {accountType,customerIds} | Account; 201 nuevo con Location; 200 replay idéntico; 400 entrada inválida; 404 cliente inexistente; 409 clave distinta/legacy pendiente; 401/403; 503 dependencia/BD | accountId positivo; tipos soportados; IDs únicos; validar Customer; rechazo clave usada en legacy | Account; accounts.write y customers.read | eft_account OPEN/version0 y vínculos, una transacción; sin saldo | ninguno |
| GET /api/accounts/{accountId} | sin body | Account 200; 400 ID; 404 ausente; 401/403; 503 BD | cuenta maestra existente | Account; accounts.read | ninguno | ninguno |
| PATCH /api/accounts/{accountId} | {accountType,version} | Account 200; 400 inválido; 404 ausente; 409 versión/estado cerrado; 401/403; 503 BD | tipo permitido, versión actual, OPEN | Account; accounts.write | tipo y version+1 por compare-and-set; sin cambiar titulares/saldos | ninguno |
| POST /api/accounts/{accountId}/closure | {version} | Account 200; 400; 404; 409 versión/estado; 401/403; 503 BD | versión actual; repetición con versión anterior al cierre devuelve mismo CLOSED | Account; accounts.write | OPEN→CLOSED/version+1; cierre registral, no liquidación | ninguno |
| GET /api/accounts?customerId={UUID}&limit={1..100}&offset={>=0} | query; defaults limit20/offset0 | Account[] 200; 400 query; 401/403; 503 BD | UUID obligatorio, paginación; no valida existencia cliente en esta ruta interna | Account; accounts.read | SELECT vínculos; orden ID | ninguno |

Si accountId existe en salida legacy, PUT 409 LEGACY_MIGRATION_REQUIRED; no apropiarse de titulares históricos contradictorios.
No se asigna saldo0 ni moneda: ausencia de saldo es deliberada.
Estas cuentas no se habilitan automáticamente en BFF/ATM; integración financiera queda pendiente.

## Customer Service — implementable

| Método / URI | Request | Response y HTTP | Validaciones | Responsable/autorización | Efectos persistentes | Evento |
|---|---|---|---|---|---|---|
| PUT /api/customers/{customerId} | {name} | Customer; 201/Location nuevo; 200 mismo ID/name; 400 formato; 409 mismo ID/name distinto; 401/403; 503 BD | UUID, name no blanco <=120, trim exterior; nombre no único | Customer; customers.write | eft_customer/version0; sin carga legacy | ninguno |
| GET /api/customers/{customerId} | sin body | Customer 200; 400 UUID; 404 ausente; 401/403; 503 BD | existe registro explícito | Customer; customers.read | ninguno | ninguno |
| PATCH /api/customers/{customerId} | {name,version} | Customer 200; 400; 404; 409 versión; 401/403; 503 BD | mismo name válido, versión vigente | Customer; customers.write | name/version+1; sin cambiar identidad ni vínculos | ninguno |
| GET /api/customers/{customerId}/accounts?limit=&offset= | defaults20/0 | Account[] 200; 400; 404 cliente; 401/403; 503 Account/BD | primero existe cliente; paginación válida | Customer; customers.read y accounts.read | consulta remota Account con JWT original | ninguno |

No endpoint que adivine cliente por nombre ni que elimine identidad/vínculos.
No esconder caída Account como []: una lista vacía solo significa respuesta real de Account sin asociaciones.

## Payment Service — propuesta NO implementada

Los siguientes contratos no son rutas disponibles. Hasta decidir saldo maestro/migración y cierre financiero, Payment mantiene scaffold y deny-by-default.

| Método / URI propuesto | Request | Response/HTTP propuesto | Validaciones | Responsable/autorización propuesta | Persistencia atómica propuesta | Evento con consumidor futuro |
|---|---|---|---|---|---|---|
| POST /api/payments/deposits | {accountId,amount,description}; Idempotency-Key | comprobante {operationId,type,status,accountId,amount}; 201 nuevo/200 replay; 400; 404; 409 clave/cuenta no operable; 401/403; 503 o resultado incierto | importe positivo, hasta2 decimales/17 enteros del modelo existente; sin límites/moneda inventados | Payment orchestration; payments.write + comando restringido Account | Account: crédito, movimiento, resultado idempotente y outbox juntos | DepositCompleted para proyección/reconciliación |
| POST /api/payments | {accountId,amount,description}; Idempotency-Key | comprobante; 201/200; 400; 404; 409 saldo insuficiente/clave/estado; 401/403; 503 incierto | débito académico sujeto a revisión; saldo suficiente con bloqueo | Payment; payments.write | Account: débito+movimiento+comprobante+outbox | PaymentCompleted, no integración ficticia a comercio |
| POST /api/payments/transfers | {sourceAccountId,destinationAccountId,amount,description}; Idempotency-Key | comprobante con ambas cuentas; 201/200; 400; 404; 409 saldo/clave/estado; 401/403; 503 incierto | origen y destino distintos, existen/operables; importe; lock IDs en orden | Payment; payments.write | Account: débito y crédito, dos movimientos, comprobante y outbox en UNA transacción | TransferCompleted; nunca aplicar crédito de nuevo por evento |
| GET /api/payments/operations/{operationId} | sin body | comprobante real 200; 400; 404; 401/403; 503 | identificador técnico y autorización de operación | Payment delega Account; payments.read | consulta persistente | ninguno |

No se asignan scopes payments.* a clientes actuales. operationId/key/fingerprint son identificadores técnicos futuros, no columnas presentes en dataset.
No se implementa tratamiento de tasas, comisiones, monedas, comercio externo, límites de negocio o préstamo.
Resultado incierto tras timeout se reconcilia por clave; no asumir rollback remoto.

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
## Account financiero interno — propuesta NO implementada

Completa el límite transaccional del diseño Payment; no hay rutas disponibles ni grants asociados todavía.

| Método / URI propuesto | Request | Response/HTTP | Validaciones | Responsable/autorización propuesta | Efectos persistentes | Evento |
|---|---|---|---|---|---|---|
| POST /internal/accounts/postings | {operationType: DEPOSIT/PAYMENT/TRANSFER,sourceAccountId?,destinationAccountId?,amount,description}; Idempotency-Key | comprobante {operationId,type,status,sourceAccountId?,destinationAccountId?,amount}; 201/200 replay; 400; 404; 409 clave/saldo/estado; 401/403; 503 incierto | campos según operación; cuenta operable y monto; vínculo financiero acordado; hash canónico por sub JWT verificado, tipo y key | Account; accounts.post; JWT original de orden o delegación autenticada a definir; nunca confiar actor en header libre | saldo(s), movimientos, resultado idempotente y outbox en una transacción | solo evento Completed aplicable, no comando asíncrono de crédito |
| GET /internal/accounts/postings/{operationId} | sin body | comprobante real 200; 400/404/401/403/503 | identidad técnica de origen y permiso de lectura de operación | Account; accounts.post.read; identidad/delegación según acuerdo | SELECT resultado; sin recalcular resultado | ninguno |

accounts.post/accounts.post.read y payments.* son scopes propuestos, no se conceden al operador actual.
Falta aprobar saldo/ledger, cierre financiero y delegación/autorización; no basta asignar scopes para declarar operativo el diseño.
No hay response de saldo ficticio; comprobante representa únicamente transacción confirmada. Timeout se reconcilia por la misma clave; caída antes de respuesta no prueba rollback.