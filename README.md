# Banco Legacy — Batch y Backend for Frontend

Proyecto académico de Desarrollo Backend III que moderniza procesos legacy del Banco XYZ. Las entregas anteriores incorporaron procesamiento Spring Batch sobre CSV; Semana 4 añade tres Backend for Frontend (BFF) independientes para Web, Móvil y Cajeros Automáticos.

## Arquitectura

El repositorio es un reactor Maven multi-módulo:

```text
banco-legacy/
├── banco-legacy-batch       # ETL legacy; único módulo que ejecuta jobs
├── banco-legacy-core        # lectura JDBC y servicios comunes de consulta
├── banco-legacy-web-bff     # API rica para interfaces Web
├── banco-legacy-mobile-bff  # API compacta para dispositivos móviles
└── banco-legacy-atm-bff     # API mínima y operaciones académicas de ATM
```

Cada BFF es una aplicación Spring Boot desplegable y protegida por separado. Los BFF dependen de `banco-legacy-core`; el core no depende de ellos. El módulo batch permanece aislado y no está en el classpath de ningún BFF, por lo que `BatchJobRunner` no puede ejecutarse al iniciar una API.

```text
CSV legacy -> Batch -> PostgreSQL <- Core JDBC <- Web BFF
                                             <- Mobile BFF
                                             <- ATM BFF
```

Se eligieron aplicaciones separadas porque el patrón BFF crea un backend ajustado a cada experiencia, con contratos y seguridad que pueden evolucionar independientemente. No se exponen tablas desde controllers: cada petición atraviesa controller, DTO del canal, servicio de aplicación y repositorio JDBC.

## Diferenciación por canal

| Canal | Puerto | Propósito | Respuesta |
|---|---:|---|---|
| Web | 8081 | Interfaz compleja y análisis | Titular, tipo, saldo original/procesado, tasa, 20 movimientos detallados y 10 anomalías |
| Móvil | 8082 | Consulta rápida | Resumen de tres campos y hasta 5 movimientos sin descripción |
| ATM | 8083 | Operación crítica mínima | Saldo, hasta 3 movimientos esenciales y simulación de retiro |

Los contratos no son copias con rutas diferentes. Web usa `WebAccountDashboard`; Móvil usa `MobileAccountSummary` y `MobileMovement`; ATM usa `AtmBalance`, `AtmMovement`, `WithdrawalRequest` y `WithdrawalResponse`.

## Datos legacy

El batch procesa:

- `transacciones.csv` hacia `transaccion_procesada`;
- `intereses.csv` hacia `interes_procesado`;
- `cuentas_anuales.csv` hacia `movimiento_anual_procesado`.

`banco-legacy-core` consulta el último saldo procesado por cuenta, movimientos recientes y anomalías. Se mantiene Spring JDBC; JPA no aporta una ventaja para estas consultas de tablas existentes.

## Seguridad académica

Se usa HTTP Basic con usuarios en memoria para demostrar autenticación y autorización por canal:

| Usuario | Contraseña | Rol | Canal autorizado |
|---|---|---|---|
| `web-user` | `web-pass` | `WEB` | Web |
| `mobile-user` | `mobile-pass` | `MOBILE` | Móvil |
| `atm-user` | `atm-pass` | `ATM` | ATM |

Cada BFF conoce los tres usuarios pero sólo acepta el rol de su canal. Sin credenciales responde `401`; credenciales válidas de otro canal responden `403`.

Los despachos internos de error están permitidos en la cadena de seguridad para que un fallo de infraestructura conserve su código `500` en lugar de quedar enmascarado como `403`. Esta excepción no permite acceder a endpoints de otro canal.

Esta configuración es deliberadamente académica. Las contraseñas están en memoria y sin cifrado persistente; no reemplaza IAM, MFA, rotación de secretos ni políticas bancarias reales.

## Requisitos y base de datos

- Java 21
- Maven 3.x
- PostgreSQL
- base `banco_legacy_batch`
- datasets `bank_legacy_data`

```powershell
$env:DB_URL = 'jdbc:postgresql://localhost:5432/banco_legacy_batch'
$env:DB_USER = 'tu_usuario'
$env:DB_PASSWORD = 'tu_clave'
```

El batch crea las tablas mediante su `schema.sql`. Los BFF usan `spring.sql.init.mode=never`: sólo leen estructuras previamente creadas.

## Ejecutar el batch

Desde la raíz:

```powershell
$env:BATCH_INPUT_DIR = 'C:/ruta/bank_legacy_data/data/semana_3'
mvn -pl banco-legacy-batch spring-boot:run
```

Los jobs `transaccionesDiariasJob`, `interesesMensualesJob` y `estadosCuentaAnualesJob` se ejecutan secuencialmente; sus particiones trabajan en paralelo. Las variables `BATCH_GRID_SIZE`, `BATCH_THREAD_COUNT`, `BATCH_CHUNK_SIZE` y `BATCH_SKIP_LIMIT` controlan el escalado.

## Ejecutar los BFF

Primero genere los ejecutables desde la raíz:

```powershell
mvn clean package
```

Después abra una terminal para cada aplicación:

```powershell
java -jar banco-legacy-web-bff/target/banco-legacy-web-bff-0.0.1-SNAPSHOT.jar
java -jar banco-legacy-mobile-bff/target/banco-legacy-mobile-bff-0.0.1-SNAPSHOT.jar
java -jar banco-legacy-atm-bff/target/banco-legacy-atm-bff-0.0.1-SNAPSHOT.jar
```

Los puertos se pueden cambiar con `WEB_BFF_PORT`, `MOBILE_BFF_PORT` y `ATM_BFF_PORT`.

## Endpoints y ejemplos

### Web

```http
GET /api/web/accounts/{accountId}/dashboard
```

```powershell
curl.exe -u web-user:web-pass http://localhost:8081/api/web/accounts/101/dashboard
```

### Móvil

```http
GET /api/mobile/accounts/{accountId}/summary
GET /api/mobile/accounts/{accountId}/movements
```

```powershell
curl.exe -u mobile-user:mobile-pass http://localhost:8082/api/mobile/accounts/101/summary
curl.exe -u mobile-user:mobile-pass http://localhost:8082/api/mobile/accounts/101/movements
```

### ATM

```http
GET  /api/atm/accounts/{accountId}/balance
GET  /api/atm/accounts/{accountId}/movements
POST /api/atm/accounts/{accountId}/withdrawals
```

```powershell
curl.exe -u atm-user:atm-pass http://localhost:8083/api/atm/accounts/101/balance
curl.exe -u atm-user:atm-pass http://localhost:8083/api/atm/accounts/101/movements
curl.exe -u atm-user:atm-pass -H "Content-Type: application/json" -d '{"amount":100}' http://localhost:8083/api/atm/accounts/101/withdrawals
```

Evidencia de autorización denegada:

```powershell
curl.exe -i http://localhost:8081/api/web/accounts/101/dashboard
curl.exe -i -u mobile-user:mobile-pass http://localhost:8081/api/web/accounts/101/dashboard
```

## Limitación del retiro

El dataset no contiene una cuenta transaccional con bloqueo, libro mayor, disponibilidad en tiempo real ni control de concurrencia. Por ello el endpoint ATM valida monto positivo y fondos contra el último `saldo_procesado`, pero devuelve estado `SIMULATED` y un saldo proyectado. No modifica PostgreSQL ni afirma ejecutar un retiro bancario real.

Tampoco existe en los datos legacy una relación entre `transaccion_procesada` y `cuenta_id`; las anomalías se presentan como información global sólo en el dashboard Web académico.

## Tests y build

Suite completa:

```powershell
.\mvnw.cmd clean verify
```

Por módulo:

```powershell
mvn -pl banco-legacy-batch test
mvn -pl banco-legacy-core test
mvn -pl banco-legacy-web-bff -am test
mvn -pl banco-legacy-mobile-bff -am test
mvn -pl banco-legacy-atm-bff -am test
```

Build completo:

```powershell
mvn clean verify
```

Las pruebas cubren la regresión batch, repositorio JDBC, carga de contexto de cada BFF, DTOs diferenciados, endpoints autorizados, peticiones sin autenticar, roles cruzados, validación y simulación de retiro.

## Relación con la pauta

1. **Comprensión BFF:** tres backends desplegables se sitúan entre cada frontend y el acceso común a datos; no se confunden con el batch.
2. **Estrategia implementada:** monorepo multi-módulo con core mínimo y aplicaciones Web, Móvil y ATM independientes.
3. **Personalización:** cada canal tiene endpoints, límites y DTOs propios; Web es rico, Móvil compacto y ATM restringido a operación esencial.
4. **Organización:** persistencia y servicios comunes viven en core; controllers, DTOs y seguridad permanecen dentro del canal correspondiente.

## Continuidad y precauciones

El batch conserva sus 11 pruebas y resultados de Semana 3: 3000 entradas, 1325 escrituras y 1675 skips. Reejecutarlo inserta nuevamente resultados; para evidencia exacta use tablas limpias o compare conteos antes y después. No se clonan ni reemplazan datasets desde este proyecto.
