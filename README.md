# Banco Legacy — Desarrollo Backend III, Semana 8

Monorepo Maven de 10 módulos que integra procesamiento batch, microservicios, OAuth 2.0, tolerancia a fallos,
mensajería Kafka y un despliegue local reproducible con Docker Compose.

El estado final validado de Semana 8 incluye 9 imágenes propias y 11 servicios Compose. El flujo comprobado es:

```text
CSV -> Batch -> PostgreSQL -> Outbox -> Kafka -> Anomaly Service
                                  |
Cliente -> Authorization Server -> Mobile BFF -> Account Service -> PostgreSQL
```

La prueba end-to-end se realizó con la cuenta `101` y respondió `HTTP 200`.

## Arquitectura

- `banco-legacy-config-server`: configuración centralizada. En Compose se accede como `http://config-server:8888`.
- `banco-legacy-discovery-server`: registro y descubrimiento Eureka.
- `banco-legacy-auth`: Spring Authorization Server, JWT RS256 y grant `client_credentials`.
- `banco-legacy-account-service`: servicio interno de dominio consumido por los tres BFF.
- `banco-legacy-web-bff`, `banco-legacy-mobile-bff`, `banco-legacy-atm-bff`: fachadas HTTPS con Resource Server y Resilience4j.
- `banco-legacy-batch`: procesa los CSV académicos, persiste resultados y publica la outbox.
- `banco-legacy-anomaly-service`: consumidor Kafka idempotente con retry y DLT.
- `banco-legacy-core`: contratos y acceso JDBC compartidos.

Todos los Config Clients conservan este comportamiento:

```properties
spring.config.import=configserver:${CONFIG_SERVER_URL:http://localhost:8888}
```

El valor predeterminado sirve para una ejecución local sin Docker. Compose inyecta
`CONFIG_SERVER_URL=http://config-server:8888` para la comunicación entre contenedores.

## Despliegue recomendado: Docker Compose

Requiere Docker Desktop, PowerShell 7, Maven 3.x y que los CSV existan en
`../bank_legacy_data/data/semana_3` respecto de este repositorio. La ruta se puede cambiar con `BATCH_DATA_DIR`.

Desde la raíz del proyecto:

```powershell
pwsh ./scripts/initialize-compose-environment.ps1
docker-compose -f docker-compose.yaml config --quiet
docker-compose -f docker-compose.yaml build
docker-compose -f docker-compose.yaml up -d
docker-compose -f docker-compose.yaml ps
```

El inicializador crea `.env` y el material local bajo `.local/compose/`; ambos están ignorados por Git. Es
idempotente: reutiliza secretos, claves y certificados válidos, y genera únicamente lo que falte. La rotación completa
es una operación explícita:

```powershell
pwsh ./scripts/initialize-compose-environment.ps1 -RotateSecrets
```

No se deben copiar valores reales a `.env.example`, documentación ni archivos versionados. El flujo Compose normal
reutiliza los secretos almacenados localmente en `.env`.

| Servicio publicado | Dirección desde el host |
|---|---|
| Config Server | `http://localhost:8888` |
| Eureka | `http://localhost:8761` |
| Web BFF | `https://localhost:8081` |
| Mobile BFF | `https://localhost:8082` |
| ATM BFF | `https://localhost:8083` |
| Authorization Server | `https://localhost:8084` |
| Account Service | `https://localhost:8085` |
| PostgreSQL Compose | `localhost:5433`, base `banco_legacy_batch` |

Dentro de la red Compose, PostgreSQL escucha como `postgres:5432`, Kafka como `kafka:9092` y Config Server como
`config-server:8888`. Esos nombres internos no deben sustituirse por `localhost` en la comunicación entre contenedores.

La guía detallada de construcción, arranque, carga y detención está en
[docs/semana-8-docker.md](docs/semana-8-docker.md).

## Primera carga de una base Docker nueva

El servicio `batch` estable arranca con `BATCH_RUN_ON_STARTUP=false`; por tanto, `docker-compose up` no importa los CSV
ni duplica datos. Para una base Docker nueva, detenga temporalmente ese servicio y ejecute una instancia de importación:

```powershell
docker-compose -f docker-compose.yaml stop batch
docker-compose -f docker-compose.yaml run -d --no-deps `
  -e BATCH_RUN_ON_STARTUP=true `
  --name banco-legacy-batch-import batch
docker logs -f banco-legacy-batch-import
```

Después de comprobar que `transaccionesDiariasJob`, `interesesMensualesJob` y `estadosCuentaAnualesJob` terminaron con
estado `COMPLETED`, finalice la observación con `Ctrl+C` y restaure el servicio normal:

```powershell
docker stop banco-legacy-batch-import
docker rm banco-legacy-batch-import
docker-compose -f docker-compose.yaml up -d batch
```

Los CSV se montan en `/data`. La validación final produjo `PUBLISHED=63`, `PENDING=0` y
`processed_anomaly_event=63`.

### Arranques posteriores

Con el volumen PostgreSQL ya poblado, use solamente:

```powershell
docker-compose -f docker-compose.yaml up -d
```

No vuelva a ejecutar la importación salvo que se trate conscientemente de una base nueva. `docker-compose down`
conserva el volumen; `docker-compose down -v` lo elimina y no forma parte del flujo normal.

## OAuth 2.0 y prueba Mobile

El Authorization Server expone `POST /oauth2/token`. Los tres consumidores técnicos usan `client_credentials`; para
Mobile el cliente es `banco-mobile-bff`, el scope es `accounts.mobile` y los tokens se firman con RSA/RS256.

Ejemplo PowerShell 7 que no imprime el secreto:

```powershell
$values = Get-Content .env | ConvertFrom-StringData
$basic = [Convert]::ToBase64String(
  [Text.Encoding]::UTF8.GetBytes("banco-mobile-bff:$($values.OAUTH_MOBILE_CLIENT_SECRET)")
)
$tokenResponse = Invoke-RestMethod -SkipCertificateCheck -Method Post `
  -Uri https://localhost:8084/oauth2/token `
  -Headers @{ Authorization = "Basic $basic" } `
  -ContentType application/x-www-form-urlencoded `
  -Body 'grant_type=client_credentials&scope=accounts.mobile'

Invoke-RestMethod -SkipCertificateCheck `
  -Uri https://localhost:8082/api/mobile/accounts/101/summary `
  -Headers @{ Authorization = "Bearer $($tokenResponse.access_token)" }
```

Los BFF propagan el Bearer token a Account Service. Los Resource Servers validan la clave pública RSA; el issuer externo
se mantiene en `https://localhost:8084` para que los tokens obtenidos desde el host sean válidos en todo el stack.

## Ejecución local sin Docker

La ejecución directa desde IntelliJ o Maven usa por defecto Config Server en `http://localhost:8888`, PostgreSQL local
en `5432` con la base `banco_legacy_batch` y los puertos HTTPS indicados arriba. `scripts/initialize-demo-session.ps1` carga secretos y claves en la
sesión actual de PowerShell; los procesos iniciados desde esa misma sesión los heredan. Si una aplicación se inicia
directamente desde una Run Configuration de IntelliJ, sus variables deben configurarse allí o IntelliJ debe haberse
abierto desde la sesión preparada.

`application-local.properties` fue una ayuda diagnóstica local, está ignorado por Git y no forma parte de la entrega.
La configuración entregable utiliza `application.properties` y Config Server.

## Verificación

La verificación final del reactor completo se ejecuta con:

```powershell
mvn verify
```

El resultado validado fue `BUILD SUCCESS` para los 10 módulos. Las evidencias de imágenes, servicios, jobs, Kafka,
OAuth end-to-end y Maven están indexadas en
[docs/evidence/semana-8/README.md](docs/evidence/semana-8/README.md).

## Documentación histórica

- [Semana 5](docs/semana-5.md): seguridad y HTTPS de esa etapa; el endpoint personalizado `/auth/token` quedó obsoleto.
- [Semana 6](docs/semana-6.md): BFF y Resilience4j.
- [Semana 7](docs/semana-7.md): introducción de Kafka, outbox, retry/DLT e idempotencia.
- [Semana 8 Docker Compose](docs/semana-8-docker.md): estado operativo vigente.

Los scripts y colecciones que conservan nombres `Week5`, `Week6` o `Week7` sirven como evidencia histórica de esas
entregas y no sustituyen el flujo OAuth2 ni el despliegue Compose de Semana 8.
