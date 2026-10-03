# Evidencias — Semana 8

Estas capturas corresponden al estado final validado del despliegue local. No contienen secretos reales.

| Evidencia | Archivo |
|---|---|
| 9 imágenes propias construidas | [Docker: 9 imágenes](semana-8-evidencia-docker.images.9-aplicaciones.png) |
| 11 servicios levantados | [Docker Compose: arranque](semana-8-evidencia-docker-compose-up-11-servicios.png) |
| Estado de los 11 servicios | [Docker Compose: ps](semana-8-evidencia-docker-compose-ps-11-servicios.png) |
| Tres jobs batch en `COMPLETED` | [Batch: jobs](semana-8.evidencia-batch-3-jobs-status-COMPLETED.png) |
| Outbox sin pendientes | [Kafka: outbox](semana-8-evidencia-kafka-outbox-correcto.png) |
| Consumo de eventos | [Kafka: consumo](semana-8-evidencia-kafka-consumo-eventos.png) |
| Spring Authorization Server: `client_credentials`, cliente `banco-mobile-bff`, scope `accounts.mobile` y token Bearer JWT con `HTTP 200` | [OAuth2: emisión del token Mobile](semana-8-evidencia-token-mobile-200.png) |
| OAuth2 Mobile end-to-end con `HTTP 200` | [OAuth2: flujo Mobile](semana-8-evidencia-oauth2-end-to-end-mobile-200.png) |
| Resilience4j: fallback controlado con Account Service detenido y Mobile BFF operativo, `HTTP 503` | [Resilience4j: dependencia no disponible](semana-8-evidencia-resilience4j-fallback-account-service-unavailable-503.png) |
| Resilience4j: recuperación de la misma petición tras restaurar Account Service, cuenta `101`, saldo `8160.00`, tipo `prestamo`, `HTTP 200` | [Resilience4j: recuperación](semana-8-evidencia-resilience4j-recovery-account-service-200.png) |
| Reactor completo con `BUILD SUCCESS` | [Maven: verify](semana-8-evidencia-maven-verify-build-success.png) |

## Secuencia validada de tolerancia a fallos

```text
servicio disponible -> HTTP 200
account-service detenido -> HTTP 503 controlado
account-service restaurado -> HTTP 200
```

Durante la indisponibilidad, Mobile BFF permaneció operativo y respondió:

```json
{
  "code": "ACCOUNT_SERVICE_UNAVAILABLE",
  "message": "La información bancaria no está disponible temporalmente"
}
```

La guía que explica cómo reproducir estas comprobaciones está en
[Semana 8: despliegue local con Docker Compose](../../semana-8-docker.md).
