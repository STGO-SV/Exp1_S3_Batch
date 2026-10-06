# Despliegue EFT

Entregable de raíz para el evaluador. La guía interna [docs/eft/despliegue.md](docs/eft/despliegue.md) conserva el procedimiento técnico validado; esta versión lo consolida y añade preparación cloud sin afirmar ejecución.

## A. Despliegue local validado

Preparación/inicializador/CSV/OAuth: [instrucciones.md](instrucciones.md). Base 13 servicios, Customer/Account/Payment 1+1+1 y tres BFF singleton. PostgreSQL compartido con volumen persistente `banco-legacy_postgres-data`; no repetir importaciones Batch ni resetear datos.

### Base

```powershell
pwsh ./scripts/initialize-compose-environment.ps1
docker compose -f docker-compose.yaml config --quiet
docker compose -f docker-compose.yaml build
docker compose -f docker-compose.yaml up -d
docker compose ps
```

### Pruebas previas y build afectado

```powershell
./scripts/test-eft-scale.ps1 -TestMode Focused -LogName etapa6-focused.log
./scripts/test-eft-scale.ps1 -TestMode Verify -LogName etapa6-verify-before.log
docker compose build customer-service account-service payment-service web-bff mobile-bff atm-bff
python -B scripts/validate-eft-scale.py config
```

### Escenario 2+2+2

```powershell
docker compose -f docker-compose.yaml -f docker/compose.scale.yaml up -d --no-deps --wait --wait-timeout 240 --scale customer-service=2 --scale account-service=2 --scale payment-service=2 customer-service account-service payment-service web-bff mobile-bff atm-bff
python -B scripts/validate-eft-scale.py ready
python -B scripts/validate-eft-scale.py routing
python -B scripts/validate-eft-scale.py flow
python -B scripts/validate-eft-scale.py outbox
python -B scripts/validate-eft-scale.py failover
```

Ejecutar la secuencia de fixtures solamente en una copia de evaluación preparada sin state scale previo. En el entorno actual el state ya existe: **no repetir flow, no borrar state y no repetir outbox como si aún fuera la captura inicial de dos owners**. Esa fase comprueba el escenario financiero anterior a failover; los owners de procesos posteriores pueden ser distintos legítimamente. ready/routing requieren dos instancias; final requiere el retorno a una.

Override elimina binding host Account con !reset, anuncia IDs/IPs únicos, verifica health por CA y configura transporte TLS/retry. Solo negocios se escalan; BFF se recrean pero no replican; --no-deps preserva infraestructura. Runner consulta Eureka, conecta a IP elegida con DNS TLS lógico y registra access logs de ambas réplicas. failover detiene una instancia y restaura en finally; no usar compose stop sobre un servicio completo replicado.

### Regreso seguro a 1+1+1

```powershell
docker compose -f docker-compose.yaml up -d --no-deps --wait --wait-timeout 240 --scale customer-service=1 --scale account-service=1 --scale payment-service=1 customer-service account-service payment-service web-bff mobile-bff atm-bff
python -B scripts/validate-eft-scale.py final
./scripts/test-eft-scale.ps1 -TestMode Verify -LogName etapa6-verify-final.log
```

Se restaura host 8085, defaults y singleton. final requiere state/snapshot de su ejecución y verifica filas previas, balances, Eureka, group/lag, BFF, HTTPS e IDs de infraestructura. Conserva fixtures del run. **Prohibido down -v, borrar volúmenes o resetear tablas.**

Resultado ejecutado Etapa 6: seis registros Eureka UP, routing en dos réplicas por servicio, failover individual, publishers coordinados, grupo de dos consumidores/3 particiones/lag 0 y retorno a 13 healthy. Ver [informe](docs/eft/informe-etapa-6-escalabilidad.md) y [estado final](docs/evidence/eft/etapa6-12-compose-final.json). Etapa 7 no volvió a ejecutar escalado.

## B. Despliegue cloud — pendiente de ejecución en laboratorio docente

**Procedimiento conceptual propuesto; no ejecutado todavía.** Despliegue cloud: **PENDIENTE DE EJECUCIÓN EN LABORATORIO DOCENTE**. Se recibió invitación a un laboratorio nuevo para despliegue real de contenedores. La sección conceptual AWS se conserva como referencia; será sustituida/complementada con el procedimiento efectivamente ejecutado y sus evidencias. No se han creado recursos, publicado imágenes cloud ni validado DNS externo. Esta corrección documental no diseña ni ejecuta recursos.

| Área | Preparación necesaria | Mapeo AWS conceptual, sin recursos existentes |
|---|---|---|
| Imágenes | Build Dockerfile parametrizado por MODULE; versiones inmutables/digest, escaneo y promoción del artefacto validado | ECR |
| Orquestación | Servicios separados, límites/requests, despliegue gradual, health y parada ordenada; Batch como ejecución única coordinada | ECS o EKS, decidir uno |
| Secretos | Migrar .env/JWT/stores a secretos gestionados, acceso mínimo por workload, rotación y reinicio controlados; no publicar valores | Secrets Manager / Parameter Store |
| PKI | Certificados con DNS reales, truststores por servicio, issuer OAuth y renovación; HTTPS al ingress no resuelve por sí solo TLS interno | ACM para listener público; PKI interna por definir |
| PostgreSQL | Red privada, cifrado, usuarios por ownership, pools/límites, HA y restore probado; la BD compartida local es una simplificación | RDS PostgreSQL |
| Kafka | Revisar brokers/particiones/retención/RF y auth TLS/SASL, consumer group compartido; contrato/outbox/dedup permanecen | MSK u oferta Kafka equivalente compatible |
| Config/discovery | Distribuir configuración sin secretos en Git; resolver IP/DNS por instancia y estrategia HA; validar convergencia/retry real | Config/Eureka autogestionados o reemplazo equivalente a diseñar |
| Ingress | Rutas hacia BFF/Auth, balanceo y readiness; no exponer DB, Kafka ni endpoints internos financieros | ALB / ingress del orquestador |
| Health | Liveness separada de readiness, verificación TLS y dependencias según ruta; despliegue sin tráfico a instancias no preparadas | Health del servicio/target group |
| Observabilidad | Logs sin JWT/PII, correlación operationId/eventId, métricas de outbox/lag/circuitos, alertas y trazas | CloudWatch u observabilidad equivalente |
| Migraciones | Ejecutar un job versionado y exclusivo antes de múltiples réplicas; revisar inicializadores always; expansión compatible/validación | Pipeline + job de migración |
| Variables | CONFIG_SERVER_URL, datasource, bootstrap Kafka, issuer/audience, stores/TLS DNS lógico, IDs únicos, group, lease/retry y pools | Config de servicio + referencias a secretos |
| Escalado | Mínimo dos réplicas Customer/Account/Payment, métricas representativas, DB locks/lease y group compartido; medir capacidad | Auto Scaling del servicio |
| Backups | Política y prueba de restauración DB, retención de eventos, protección de claves y configuración; RPO/RTO por acordar | Backups RDS / políticas de retención |
| Rollback | Volver al digest previo compatible, preservar datos; no deshacer columnas aditivas mientras consumidores las requieran | Revisión previa de servicio/pipeline |
| Red | Subredes privadas, security groups mínimos, salida controlada, auth de broker/DB, acceso de administración auditado | VPC / security groups / IAM |

Antes de una ejecución posterior autorizada: revisar las instrucciones/recursos del laboratorio docente, sus restricciones y parámetros reales. No se fijan aquí recursos, región, IDs, ARNs, dominio o costos. La guía de laboratorio y los resultados reales determinarán el procedimiento final; esta sección no contiene comandos AWS ejecutados.

La inyección de secretos desde Secrets Manager/Parameter Store se documenta en [Amazon ECS: datos sensibles](https://docs.aws.amazon.com/AmazonECS/latest/developerguide/specifying-sensitive-data.html). La separación de seguridad de red, certificados del listener y cifrado hacia contenedores se apoya en [Amazon ECS: seguridad de red](https://docs.aws.amazon.com/AmazonECS/latest/developerguide/security-network.html). Estas referencias sustentan la preparación conceptual, no una validación del proyecto en AWS.
