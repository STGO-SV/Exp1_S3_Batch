# Banco Legacy — Desarrollo Backend III, Semana 5

Monorepo Maven que conserva el procesamiento legacy de semanas anteriores y entrega tres Backend for Frontend (BFF)
independientes para Web, Mobile y ATM. Semana 5 incorpora Bearer JWT, un emisor local mínimo, optimización JDBC
y HTTPS directo en las cuatro aplicaciones Spring Boot mediante PKCS12 local.

**Estado:** código, JWT y TLS directo implementados. La evidencia HTTPS con H2 no sustituye la ejecución final con PostgreSQL.

## Arquitectura

```text
Cliente / Postman ──HTTPS──> Web BFF :8081 ─────┐
                         ├──> Mobile BFF :8082 ──┼──> Core JDBC ──> PostgreSQL
                         └──> ATM BFF :8083 ─────┘

Cliente local ──HTTPS──> Auth :8084 ──> JWT RS256
                                      clave privada   clave pública → cada BFF

CSV legacy ──> Batch ──> PostgreSQL   (no ejecutar para iniciar los BFF)
```

| Módulo | Responsabilidad |
|---|---|
| banco-legacy-batch | Jobs legacy, separado de los BFF |
| banco-legacy-core | Modelos y consultas JDBC comunes, sin seguridad ni emisión |
| banco-legacy-auth | Infraestructura local de autenticación y emisión JWT; sin datos bancarios |
| banco-legacy-web-bff | Contrato Web completo |
| banco-legacy-mobile-bff | Contrato compacto Mobile |
| banco-legacy-atm-bff | Saldo y movimientos mínimos, retiro simulado |

Los BFF dependen únicamente de Core entre los módulos propios. No dependen de Auth para compilar ni consultan al emisor
para validar cada petición: usan su clave pública. Cada aplicación conserva su cadena de seguridad, puerto y JAR.
El escaneo de componentes se limita al canal y Core. No se añadió IdP empresarial ni filtros JWT manuales.

## Contratos y optimización

| Endpoint | Respuesta | Consultas por petición exitosa |
|---|---|---:|
| GET /api/web/accounts/{accountId}/dashboard | 8 campos: cuenta, titular, tipo, saldo original, tasa, saldo procesado, hasta 20 movimientos de 4 campos y 10 anomalías de 4 campos | 3, antes 4 |
| GET /api/mobile/accounts/{accountId}/summary | accountId, balance, accountType | 1, dos columnas SQL |
| GET /api/mobile/accounts/{accountId}/movements | Hasta 5: date, type, amount | 2: EXISTS + proyección |
| GET /api/atm/accounts/{accountId}/balance | accountId, availableBalance | 1, una columna SQL |
| GET /api/atm/accounts/{accountId}/movements | Hasta 3: type, amount | 2: EXISTS + proyección |
| POST /api/atm/accounts/{accountId}/withdrawals | accountId, requestedAmount, balanceBefore, projectedBalance, status, message | 1, sin escritura |

Web deja de leer el saldo dos veces en la misma petición. Mobile no trae descripciones y ATM no trae fechas/descripciones
para movimientos. Los LIMIT se aplican en SQL; orden por fecha DESC, id DESC. Saldo más reciente por id DESC.
Las consultas de movimientos verifican existencia sin cargar todo el saldo: cuenta inexistente da 404 y cuenta existente
sin movimientos da lista vacía. Las anomalías Web son globales porque el dataset no las relaciona con cuenta.

No se añadió caché, compresión ni índices sin medición. [SQL de inspección](docs/sql/explain-bff.sql) contiene exclusivamente
EXPLAIN ANALYZE de SELECT dentro de una transacción READ ONLY. Planes PostgreSQL y decisiones de índices están pendientes.

## Requisitos y variables

Java 21, Maven 3.x (o wrapper), PostgreSQL con datos existentes, PowerShell 7 para scripts; Docker Desktop para NPM.
Versiones del proyecto: Spring Boot 3.3.4, Spring Security 6.3.3. NPM: imagen del proyecto jc21/nginx-proxy-manager:2.15.1.

| Variable | Aplicación | Valor / formato |
|---|---|---|
| DB_URL | BFF | JDBC PostgreSQL; obligatoria |
| DB_USER | BFF | Usuario PostgreSQL; obligatorio |
| DB_PASSWORD | BFF y Batch | Obligatoria; sin contraseña predeterminada |
| JWT_PUBLIC_KEY | Auth y BFF | Clave RSA pública X.509 DER codificada en Base64; obligatoria |
| JWT_PRIVATE_KEY | Sólo Auth | Clave RSA privada PKCS#8 DER en Base64; obligatoria |
| JWT_ISSUER | Auth y BFF | banco-legacy-auth por defecto; valor idéntico en todos |
| JWT_AUDIENCE | Auth y BFF | banco-bff por defecto; audience común |
| JWT_TTL_SECONDS | Sólo Auth | 300 por defecto; intervalo permitido 30–900 |
| DEMO_WEB_PASSWORD | Sólo Auth | Contraseña externa de web-user |
| DEMO_MOBILE_PASSWORD | Sólo Auth | Contraseña externa de mobile-user |
| DEMO_ATM_PASSWORD | Sólo Auth | Contraseña externa de atm-user |
| WEB_BFF_PORT / MOBILE_BFF_PORT / ATM_BFF_PORT | BFF | 8081 / 8082 / 8083 |
| AUTH_PORT / AUTH_ADDRESS | Auth | 8084 / 127.0.0.1 |

Contraseñas demo de 12 caracteres como mínimo y hasta 72 bytes UTF-8, procesadas con BCrypt al iniciar Auth.
No hay contraseñas demo publicadas. Los BFF no tienen UserDetailsService ni usuarios en memoria.

Para PostgreSQL real use las variables conocidas en su sesión. Ejemplo sin contraseña literal:

```powershell
$env:DB_URL = 'jdbc:postgresql://localhost:5432/banco_legacy_batch'
$env:DB_USER = 'postgres'
$env:DB_PASSWORD = [Net.NetworkCredential]::new('', (Read-Host 'Contraseña PostgreSQL' -AsSecureString)).Password
```

Se recomienda un usuario de sólo lectura para los BFF. Hikari se configura read-only y SQL init está deshabilitado;
esto no reemplaza los permisos PostgreSQL. No se crean usuarios, índices ni tablas reales automáticamente.

## Compilar y ejecutar

```powershell
mvn clean verify
. ./scripts/initialize-demo-session.ps1
./scripts/start-week5.ps1
```

El primer comando compila y prueba todos los módulos. Los tests batch sólo usan H2; requieren los datasets existentes
de Semana 1 y 3. Por defecto buscan el directorio hermano bank_legacy_data/data. Si están en otra ruta:

```powershell
mvn clean verify "-Dbatch.test-data-root=C:/ruta/bank_legacy_data/data"
```

initialize-demo-session genera un par RSA de firma JWT en memoria y solicita las tres contraseñas sin mostrarlas.
**No genera certificados TLS** y no escribe secretos. Las variables duran sólo en esa sesión y sus procesos hijos.
Si se reinicia la sesión, deben reiniciarse emisor y BFF con el mismo nuevo par; tokens anteriores dejan de ser válidos.

start-week5 inicia únicamente Auth y los tres BFF; muestra sus PID. No inicia Batch. Separa el entorno:
los BFF no reciben clave privada ni contraseñas demo; Auth no recibe las credenciales DB.
Para detenerlos, usar exclusivamente los PID devueltos. Con -AuthOnly inicia sólo el emisor.
Los errores de arranque se muestran en consola; comprobar que los cuatro servicios hayan iniciado.

También pueden ejecutarse desde IntelliJ usando las principales de cada módulo y las variables correspondientes.
Las configuraciones de IntelliJ ya abiertas no heredan cambios de una terminal: introducir variables en cada Run Configuration
local o iniciar el IDE desde la sesión preparada. No compartir/exportar configuraciones con secretos.

**No iniciar BancoLegacyBatchApplication contra PostgreSQL.** Su runner propio inserta resultados nuevamente.
spring.batch.job.enabled=false no desactiva ese runner; la separación Maven evita que arranque con un BFF.

## JWT, autenticación y autorización

POST http://127.0.0.1:8084/auth/token recibe JSON con username y password.
Es un emisor académico de tokens, no un Authorization Server OAuth2 completo.
Sólo es accesible por loopback por defecto; no enviar contraseñas a ese HTTP desde otra máquina.

Ejemplo usando una variable de entorno, sin imprimir el token:

```powershell
$login = @{ username='web-user'; password=$env:DEMO_WEB_PASSWORD } | ConvertTo-Json
$token = Invoke-RestMethod -SkipCertificateCheck -Method Post -Uri 'https://localhost:8084/auth/token' -ContentType 'application/json' -Body $login
$headers = @{ Authorization="Bearer $($token.access_token)" }
Invoke-RestMethod -SkipCertificateCheck -Uri 'https://localhost:8081/api/web/accounts/101/dashboard' -Headers $headers
```

No registrar $login, $headers o $token ni exportar variables secretas. Auth responde access_token, token_type=Bearer y expires_in;
incluye Cache-Control: no-store. Credenciales incorrectas dan 401 y cuerpo de login inválido 400.

Claims: iss, sub, aud, iat, nbf, exp, jti y roles (lista WEB, MOBILE o ATM).
Firma RS256 con RSA de al menos 2048 bits. NimbusJwtEncoder y NimbusJwtDecoder de Spring Security.
Cada BFF valida firma, issuer, audience, tiempos y presencia/tipos de claims requeridos. La tolerancia temporal estándar es 60 s.
roles se transforma en ROLE_WEB / ROLE_MOBILE / ROLE_ATM. Audience común permite demostrar rol incorrecto como 403,
mientras audience o issuer incorrectos dan 401. Los roles los asigna Auth, no el JSON del cliente.

| Credencial | Web | Mobile | ATM |
|---|---:|---:|---:|
| JWT WEB válido | 200 | 403 | 403 |
| JWT MOBILE válido | 403 | 200 | 403 |
| JWT ATM válido | 403 | 403 | 200 |
| Sin token / Basic / JWT malformado, alterado o expirado | 401 | 401 | 401 |

200 supone endpoint/cuenta válidos. Cuenta ausente da 404. Error interno autorizado conserva 500;
DispatcherType.ERROR está permitido, pero una petición externa normal a /error no se permite.

SessionCreationPolicy.STATELESS y request cache desactivado. CSRF se deshabilita porque la autenticación de la API
usa Authorization Bearer y no cookies de sesión; no se habilita HTTP Basic ni formulario como alternativa final.
Si en el futuro se usan cookies de autenticación, debe revisarse esa decisión.

## HTTPS directo y Postman

```powershell
./scripts/generar-certificados.ps1
# Alternativa Git Bash: bash scripts/generar-certificados.sh
```

Los scripts adaptan el procedimiento del profesor: generan un PKCS12 RSA 2048 de laboratorio, alias `bff-local`,
CN `localhost`, SAN `localhost`/`127.0.0.1` y vigencia de 365 días. Copian el mismo keystore a Auth y los tres BFF.
El archivo contiene clave privada y está ignorado por Git. `changeit` es la contraseña académica predeterminada;
puede sustituirse temporalmente con `TLS_KEYSTORE_PASSWORD` y no representa gestión productiva de secretos.

La aplicación normal configura `server.ssl.*`; los tests deshabilitan TLS sólo en contextos internos. El certificado público
queda en `.local/tls/localhost.crt`, fuera de Git. No se modifica automáticamente la confianza de Windows.
NPM se conserva en `infra/` como alternativa experimental y no participa en la solución efectiva.

En Postman, desactivar **SSL certificate verification** sirve para laboratorio y demuestra que TLS responde, pero no confianza.
La modalidad estricta importa/confía el certificado local y mantiene la verificación activa. `curl.exe -k` tiene la misma
limitación; `curl.exe --cacert .local/tls/localhost.crt https://localhost:8084/...` valida la cadena local y el nombre.

Importar [colección Postman](docs/postman/semana-5.postman_collection.json) y
[plantilla de entorno](docs/postman/semana-5.postman_environment.example.json).
Definir contraseñas como valores locales secretos, ejecutar las tres solicitudes Token y luego la matriz.
No exportar el entorno con contraseñas/tokens. La colección guarda los tokens obtenidos en el entorno local.

## Retiro académico ATM

Se conserva status=SIMULATED y saldo proyectado. No se escribe PostgreSQL.
Monto requerido, positivo, máximo dos decimales y 17 dígitos enteros; se valida también en el servicio.
Fondos insuficientes mantienen HTTP 400 con code=WITHDRAWAL_REJECTED y
message="Saldo insuficiente para el retiro solicitado". No hay ledger, reservas, bloqueo ni retiro bancario real.

## Pruebas y evidencia

113 ejecuciones de tests en la suite actual, incluidos los 27 casos históricos adaptados a Bearer.
Cobertura: emisión, JWT firmado real, seis cruces, ausencia/Basic/alteración/expiración/claims inválidos,
error interno 500, contratos, repositorio, límites/orden, 404, montos y simulación sin persistencia.
H2 permite probar integración del código; no sustituye PostgreSQL real.

[Índice de evidencias](docs/evidence/semana-5/README.md).
Para repetir comprobación interna aislada tras compilar:

```powershell
./scripts/smoke-isolated.ps1
```

Inicia Auth y los tres BFF con HTTPS en 8084/8081/8082/8083 y datos sintéticos H2; los cierra al terminar.
No usa los datasets legacy ni PostgreSQL. Registra verification-https-h2.json y bff-benchmark-https-h2.csv.
El cliente confía explícitamente en .local/tls/localhost.crt y mantiene la validación del nombre localhost.

Para cerrar la evidencia final contra PostgreSQL, use desde la misma sesión PowerShell 7 que ya contiene
las variables JWT, demo y DB:

```powershell
./scripts/verify-week5-postgres.ps1
```

El script comprueba las variables sin mostrar valores, selecciona la cuenta 101 cuando es apta o busca otra
mediante una consulta READ ONLY, inicia sólo los servicios ausentes y detiene únicamente los procesos que creó.
No inicia Batch. Emite tokens nuevos por bloque sin cambiar el TTL de 300 segundos y valida el certificado
mediante .local/tls/localhost.crt.

La misma ejecución genera matriz JWT, perfiles de payload, retiro SIMULATED, comparación del saldo persistido,
EXPLAIN ANALYZE en transacción READ ONLY, benchmark de 20 muestras por endpoint y clean verify. Finaliza con
un resumen de consola pensado para una sola captura. No es necesario levantar los servicios: conviene cerrarlos
antes para que Windows no bloquee los JAR durante clean verify; el script iniciará y detendrá los suyos.
El benchmark mide cinco endpoints GET, bytes, mediana, p95 nearest-rank, muestras y errores.
Las latencias se calculan sobre respuestas 200; errores quedan explícitos. Sin thresholds Maven arbitrarios.
La comparación Web vs resumen/movimientos representa contratos distintos: sumar peticiones si se evalúa una pantalla completa.
Para sólo inspección interna, -InternalHttp permite únicamente URLs HTTP de loopback; no sirve como evidencia TLS.

La automatización no almacena contraseñas, claves ni JWT completos. Los resultados PostgreSQL sólo deben
documentarse como aprobados después de ejecutar el comando anterior en la sesión que contiene las variables.
No se agregan índices ni se modifican datos.

## Limitaciones y siguiente bloque

- Ejecutar verify-week5-postgres.ps1 desde la sesión PowerShell 7 preparada y revisar OVERALL.
- Revisar planes y sólo después decidir índices.
- Rol por canal no significa titularidad de cuenta; cualquier usuario autorizado al canal puede consultar sus accountId.
- Auth es local, sin refresh, revocación, MFA ni rate limiting; no exponer como servicio público.
- El certificado y la contraseña predeterminada son exclusivamente de laboratorio; producción requiere custodia e identidad administradas.
- Sin medición de carga/concurrencia no se afirma capacidad de producción ni 100% de pauta.
- No hay auditoría SCA completa de dependencias ni actualización mayor de Spring.
