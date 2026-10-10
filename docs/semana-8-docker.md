# Semana 8: despliegue local con Docker Compose

> Documento histórico del escenario indicado; sus estados y comandos corresponden a esa validación. El estado final EFT se describe en los documentos de raíz.

La topología Docker es autocontenida y no modifica el PostgreSQL instalado en el host. Compose crea la base
`banco_legacy_batch` en el volumen nombrado `postgres-data` y publica PostgreSQL en el puerto local `5433` para evitar
colisiones con el puerto `5432` del host.

El estado validado consta de **9 imágenes propias** y **11 servicios Compose**. Es un despliegue local; esta evaluación
no requiere AWS, Azure ni GCP.

## Preparación

Desde la raíz del repositorio, generar las credenciales efímeras, el par RSA del Authorization Server y el material TLS:

```powershell
pwsh ./scripts/initialize-compose-environment.ps1
```

El script crea `.env` y `.local/compose/`. Ambos están ignorados por Git. Las ejecuciones posteriores reutilizan los
secretos, el par RSA y los certificados válidos; sólo generan valores ausentes o material inválido. Para rotarlos de
forma intencional se debe usar `pwsh ./scripts/initialize-compose-environment.ps1 -RotateSecrets`.

El certificado incluye los SAN de
`localhost`, `auth-server`, `account-service`, `web-bff`, `mobile-bff` y `atm-bff`; el truststore permite HTTPS entre
contenedores sin desactivar la validación TLS de los clientes Java.

## Construcción y arranque

```powershell
docker-compose -f docker-compose.yaml config
docker-compose -f docker-compose.yaml build
docker-compose -f docker-compose.yaml up -d
docker-compose -f docker-compose.yaml ps
```

Los Config Clients mantienen `http://localhost:8888` como valor predeterminado para ejecución directa, pero Compose
inyecta `CONFIG_SERVER_URL=http://config-server:8888`. En la red interna también se usan `postgres:5432` para la base
`banco_legacy_batch` y `kafka:9092`; `localhost` dentro de un contenedor nunca identifica a otro servicio.

Servicios publicados en el host:

| Servicio | URL/puerto |
|---|---|
| Config Server | `http://localhost:8888` |
| Eureka | `http://localhost:8761` |
| Web BFF | `https://localhost:8081` |
| Mobile BFF | `https://localhost:8082` |
| ATM BFF | `https://localhost:8083` |
| Authorization Server | `https://localhost:8084` |
| Account Service | `https://localhost:8085` |
| PostgreSQL de Compose | `localhost:5433`, base `banco_legacy_batch` |

Kafka sólo se publica en la red interna como `kafka:9092`. Anomaly Service y Batch no exponen HTTP. Batch monta por
defecto `../bank_legacy_data/data/semana_3` como `/data`; la ruta del host puede cambiarse con `BATCH_DATA_DIR` en
`.env`. El servicio estable conserva `BATCH_RUN_ON_STARTUP=false` para no duplicar datos en cada reinicio.

Para poblar una base Docker nueva con los tres jobs académicos se crea una ejecución controlada y se detiene al terminar.
Primero se detiene el servicio `batch` estable para que sólo exista una instancia publicando la outbox:

```powershell
docker-compose -f docker-compose.yaml stop batch
docker-compose -f docker-compose.yaml run -d --no-deps `
  -e BATCH_RUN_ON_STARTUP=true `
  --name banco-legacy-batch-import batch
docker logs -f banco-legacy-batch-import
```

Cuando `transaccionesDiariasJob`, `interesesMensualesJob` y `estadosCuentaAnualesJob` aparezcan como `COMPLETED`, salir
del seguimiento con `Ctrl+C` y ejecutar:

```powershell
docker stop banco-legacy-batch-import
docker rm banco-legacy-batch-import
docker-compose -f docker-compose.yaml up -d batch
```

Los CSV académicos se leen desde `/data`. El servicio `batch` normal vuelve a quedar activo con los jobs deshabilitados
para publicar cualquier evento pendiente del outbox. En arranques posteriores se usa únicamente `docker-compose up -d`;
no se repite la importación sobre un volumen ya poblado.

## OAuth2

El issuer se conserva como `https://localhost:8084` para que los tokens obtenidos desde el host sean válidos en todos
los Resource Servers. Los servicios validan la firma con `JWT_PUBLIC_KEY`; no necesitan resolver el hostname del issuer.

Ejemplo para obtener un token Mobile sin imprimir el secreto:

```powershell
$values = Get-Content .env | ConvertFrom-StringData
$pair = [Convert]::ToBase64String([Text.Encoding]::UTF8.GetBytes("banco-mobile-bff:$($values.OAUTH_MOBILE_CLIENT_SECRET)"))
$token = Invoke-RestMethod -SkipCertificateCheck -Method Post `
  -Uri https://localhost:8084/oauth2/token `
  -Headers @{ Authorization = "Basic $pair" } `
  -ContentType application/x-www-form-urlencoded `
  -Body 'grant_type=client_credentials&scope=accounts.mobile'
```

Los secretos generados usan Base64URL, por lo que no contienen `+` ni `%` y son compatibles con clientes Basic Auth
genéricos.

Prueba end-to-end validada con la cuenta `101`:

```powershell
Invoke-RestMethod -SkipCertificateCheck `
  -Uri https://localhost:8082/api/mobile/accounts/101/summary `
  -Headers @{ Authorization = "Bearer $($token.access_token)" }
```

La cadena comprobada fue `CSV -> Batch -> PostgreSQL -> Outbox -> Kafka -> Anomaly Service -> Account Service ->
Mobile BFF -> HTTP 200`. Tras la carga se observaron `PUBLISHED=63`, `PENDING=0` y
`processed_anomaly_event=63`.

## Evidencias

Las capturas verificadas de las 9 imágenes, los 11 servicios, los tres jobs, Kafka/outbox, OAuth2 end-to-end y el
`BUILD SUCCESS` del reactor están indexadas en [evidence/semana-8/README.md](evidence/semana-8/README.md).

## Detención y datos

```powershell
docker-compose -f docker-compose.yaml down
```

El comando anterior conserva PostgreSQL. Sólo una eliminación explícita del volumen (`down -v`) borra la base Docker;
no debe usarse si se necesita conservar la evidencia local.
