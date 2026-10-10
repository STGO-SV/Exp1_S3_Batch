# Procedimiento técnico interno: despliegue local EFT

> Documento histórico de trazabilidad interna. Estado final (2026-10-10): desarrollo técnico completo; EFT integrada en main y publicada en GitHub. Despliegue en entorno AWS EC2 ejecutado y documentado; capturas finales incorporadas. Informe PDF generado con la plantilla oficial y DOCX editable disponible. Video grabado y entregado fuera del repositorio, sin enlace alojado en este árbol.

Procedimiento demostrado en Etapa 6. Docker Desktop activo y .env/.local/compose existentes. PostgreSQL usa volumen persistente banco-legacy_postgres-data. Conservar datos y state local del runner.

## Validación antes de escalar

Desde raíz, con PostgreSQL base en host 5433:

```powershell
./scripts/test-eft-scale.ps1 -TestMode Focused -LogName etapa6-focused.log
./scripts/test-eft-scale.ps1 -TestMode Verify -LogName etapa6-verify-before.log
docker compose build customer-service account-service payment-service web-bff mobile-bff atm-bff
python -B scripts/validate-eft-scale.py config
```

El helper ejecuta Maven sin mostrar credenciales; habilita PostgreSQL usando .env, aísla datos en schemas temporales y restaura variables del proceso. Exigir BUILD SUCCESS antes del escenario horizontal.

## Escenario 2+2+2

```powershell
docker compose -f docker-compose.yaml -f docker/compose.scale.yaml up -d --no-deps --wait --wait-timeout 240 --scale customer-service=2 --scale account-service=2 --scale payment-service=2 customer-service account-service payment-service web-bff mobile-bff atm-bff
python -B scripts/validate-eft-scale.py ready
python -B scripts/validate-eft-scale.py routing
python -B scripts/validate-eft-scale.py flow
python -B scripts/validate-eft-scale.py outbox
python -B scripts/validate-eft-scale.py failover
```

El override escala solo negocios; elimina binding host Account por !reset y mantiene puertos internos. Reutiliza TLS vigente y configura discovery/IPs/caché/retry. BFF se recrean para cargar transporte, conservando una réplica. --no-deps evita reiniciar infraestructura no relacionada.

El runner valida certificado/hostname, consulta Eureka y comprueba IP conectada. Access logs prueban atención por réplica sin modificar respuestas.

flow crea fixtures una vez y exige ausencia de state previo. .local/etapa6-state.json evita repetir dinero accidentalmente. Con state existente conservar runId/keys y reutilizar fases de inspección; no borrar state para ocultar un fallo.

failover detiene el contenedor individual y restaura en finally. Reutiliza keys financieras; el contador local se incrementa una vez por caso exitoso. No detener un servicio escalado completo con compose stop.

## Regreso a base 1+1+1

```powershell
docker compose -f docker-compose.yaml up -d --no-deps --wait --wait-timeout 240 --scale customer-service=1 --scale account-service=1 --scale payment-service=1 customer-service account-service payment-service web-bff mobile-bff atm-bff
python -B scripts/validate-eft-scale.py final
./scripts/test-eft-scale.ps1 -TestMode Verify -LogName etapa6-verify-final.log
```

El base retira réplicas adicionales, restaura host 8085 y defaults de clientes. final comprueba filas anteriores, finanzas del run, Eureka singleton, health, lag, infraestructura preservada, BFF legacy y HTTPS host 8085.

No ejecutar down -v, borrar volúmenes ni resetear datos. No push/merge/cloud en esta etapa. Estos comandos describen la validación local histórica de Etapa 6; la evidencia AWS posterior se documenta en la guía de raíz.

## Entregable consolidado

La versión para evaluación está en [despliegue.md de raíz](../../despliegue.md), con despliegue real AWS EC2 Amazon Linux 2023 + Docker Compose incorporado: [13 servicios base healthy](../evidence/eft/capturas/1_despliegue_contenedores_nube.png), [2+2+2/16 contenedores](../evidence/eft/capturas/aws-escalabilidad-horizontal-2x2x2.png) y [Eureka dos UP por negocio](../evidence/eft/capturas/aws-eureka-2x2x2-up.png). Failovers y recuperación se enlazan en el índice. Una única EC2; no HA multi-host/multi-AZ. Retorno final 1+1+1 registrado al cierre de la prueba; sin captura consolidada de ese cierre. Este procedimiento interno conserva los comandos probados de Etapa 6. Las fases flow/outbox tienen precondiciones de state y escenario inicial: consultar la guía de raíz antes de reproducir sobre la instancia ya validada.
