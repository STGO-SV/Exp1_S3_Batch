# Despliegue EFT

Entregable de raíz para el evaluador. La guía interna [docs/eft/despliegue.md](docs/eft/despliegue.md) conserva el procedimiento técnico validado; esta versión lo consolida el procedimiento local e incorpora el despliegue AWS EC2 realmente ejecutado y sus capturas.

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

## B. Despliegue AWS EC2 ejecutado y validado

El despliegue real se realizó en **una instancia AWS EC2 con Amazon Linux 2023 y Docker Compose**. Las capturas suministradas se incorporaron a la documentación el 8 de octubre de 2026. La plataforma/OS y el cierre de la prueba fueron informados por el usuario; las salidas capturadas corroboran contenedores, HTTP y discovery. Esta actualización documental no volvió a ejecutar el despliegue.

### Despliegue base y smoke test

La captura de `docker compose ps` muestra **13 servicios saludables**, incluidos Customer, Account y Payment con una réplica cada uno, tres BFF y plataforma/infraestructura. PostgreSQL y Kafka corren como contenedores del mismo Compose, no como servicios gestionados AWS acreditados.

| Prueba | Endpoint visible | Resultado |
|---|---|---|
| OAuth token | https://localhost:8084/oauth2/token | HTTP 200 |
| Customer | PUT https://customer-service:8087/api/customers/{UUID} | HTTP 201 |
| Account | PUT https://account-service:8085/api/accounts/101 | HTTP 201 |
| Web BFF dashboard | GET https://localhost:8081/api/web/accounts/101/dashboard | HTTP 200 y respuesta JSON legacy |

Las URLs localhost corresponden al host EC2; los nombres de servicio corresponden a su red Docker. Estas capturas no prueban acceso mediante un DNS público externo ni integración del dashboard legacy con la cuenta maestra moderna.

### Escalabilidad horizontal en nube y Eureka

Customer=2, Account=2 y Payment=2: **2+2+2**, seis contenedores de negocio y **16 contenedores totales**. Eureka muestra dos instancias **UP,UP** para cada servicio. Se trata de **escalabilidad horizontal a nivel de contenedores/microservicios en nube sobre una única EC2**. No acredita distribución multi-host/multi-AZ, Kubernetes, HA de infraestructura ni tolerancia a la pérdida de EC2.

### Failover, recuperación y cierre

- **Account:** se detiene Account-1, Account-2 permanece healthy y Web BFF responde **HTTP 200**. La recuperación muestra Account-1 healthy.
- **Customer:** se detiene Customer-1, Customer-2 permanece healthy y la operación de alta responde **HTTP 201**. Customer-1 se restaura y pasa a healthy.
- **Payment:** el usuario informa el failover de una réplica; la captura funcional muestra depósito **HTTP 201**, `status: COMPLETED` y comprobante. Esa captura no incluye el comando de detención; se complementa con Payment-1 healthy y la recuperación conjunta.
- **Restauración:** la vista conjunta acredita nuevamente **2+2+2/16 healthy** después de las pruebas.
- **Retorno final:** el usuario confirma el regreso a **una réplica por servicio de negocio (1+1+1)**. Las capturas disponibles muestran recuperación individual/conjunta, pero no una vista consolidada del estado final 1+1+1. No presentar `aws-2x2x2-restaurado-healthy.png` como prueba de reducción a 13 contenedores.

### Evidencias AWS

| Prueba | Captura | Alcance visible |
|---|---|---|
| Despliegue base en EC2 | [1_despliegue_contenedores_nube.png](docs/evidence/eft/capturas/1_despliegue_contenedores_nube.png) | docker compose ps: 13 servicios healthy. |
| OAuth en EC2 | [2_oauth_200_ec2.png](docs/evidence/eft/capturas/2_oauth_200_ec2.png) | Endpoint /oauth2/token: HTTP 200. |
| Smoke registral | [3_smoke_test_funcional_200_201_201.png](docs/evidence/eft/capturas/3_smoke_test_funcional_200_201_201.png) | OAuth 200; PUT Customer 201; PUT Account 201. |
| Smoke Web BFF | [aws-smoke-web-bff-200-dashboard.png](docs/evidence/eft/capturas/aws-smoke-web-bff-200-dashboard.png) | GET /api/web/accounts/101/dashboard: HTTP 200 y dashboard legacy. |
| Escala en nube | [aws-escalabilidad-horizontal-2x2x2.png](docs/evidence/eft/capturas/aws-escalabilidad-horizontal-2x2x2.png) | Customer=2, Account=2, Payment=2; 16 contenedores healthy sobre una única EC2. |
| Discovery en nube | [aws-eureka-2x2x2-up.png](docs/evidence/eft/capturas/aws-eureka-2x2x2-up.png) | Dos instancias UP,UP por cada uno de los tres servicios; seis registros. |
| Failover Account | [aws-failover-account-service-bff-200.png](docs/evidence/eft/capturas/aws-failover-account-service-bff-200.png) | Account-1 detenido, Account-2 healthy; Web BFF HTTP 200. |
| Failover Customer | [aws-customer-service-failover.png](docs/evidence/eft/capturas/aws-customer-service-failover.png) | Customer-1 detenido, Customer-2 healthy; creación Customer HTTP 201. |
| Failover Payment | [aws-payment-service-failover-completed-201.png](docs/evidence/eft/capturas/aws-payment-service-failover-completed-201.png) | Depósito HTTP 201 con status COMPLETED; detención de réplica indicada por el usuario. |
| Recuperación Account | [aws-recuperacion-failover.png](docs/evidence/eft/capturas/aws-recuperacion-failover.png) | Inicio de Account-1 y estado healthy tras Web BFF 200. |
| Recuperación Customer | [aws-customer-service-recuperacion-healthy.png](docs/evidence/eft/capturas/aws-customer-service-recuperacion-healthy.png) | Customer-1 pasa de health: starting a healthy. |
| Recuperación Payment | [aws-payment-service-recuperacion-healthy.png](docs/evidence/eft/capturas/aws-payment-service-recuperacion-healthy.png) | Payment-1 healthy. |
| Recuperación conjunta | [aws-2x2x2-restaurado-healthy.png](docs/evidence/eft/capturas/aws-2x2x2-restaurado-healthy.png) | Dos réplicas por negocio restauradas; 16 contenedores healthy. |

### Alcance de seguridad y reproducibilidad

Las requests capturadas usan `curl -k`: demuestran respuestas sobre HTTPS, **no validación de CA/hostname** en AWS. La verificación estricta TLS de Etapas 4–6 conserva su alcance local; no se extrapola a EC2. Las imágenes no muestran secretos/JWT en claro, sino referencias a variables; no trasladar sus valores a documentación.

Se documentan acciones/salidas observadas (listado Compose, OAuth, PUT registrales, GET dashboard, depósito, parada/inicio de réplicas y Eureka). No se inventan instrucciones de aprovisionamiento EC2, AMI/instancia, security groups, comandos de instalación o parámetros cloud ausentes de las capturas. El código, arquitectura, configuración y capturas permanecen intactos.

### Evolución cloud conceptual — no ejecutada

La tabla siguiente se conserva como propuesta futura; **ECR/ECS/EKS/RDS/MSK/ALB y otros servicios gestionados no son el despliegue EC2 acreditado**.

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

Para evolucionar a servicios gestionados se requiere un alcance posterior. No se fijan aquí recursos, región, IDs, ARNs, dominio ni costos. La evidencia actual corresponde a EC2 con Compose; la tabla conceptual no afirma ejecución de sus alternativas.

La inyección de secretos desde Secrets Manager/Parameter Store se documenta en [Amazon ECS: datos sensibles](https://docs.aws.amazon.com/AmazonECS/latest/developerguide/specifying-sensitive-data.html). La separación de seguridad de red, certificados del listener y cifrado hacia contenedores se apoya en [Amazon ECS: seguridad de red](https://docs.aws.amazon.com/AmazonECS/latest/developerguide/security-network.html). Estas referencias sustentan únicamente las alternativas conceptuales de servicios gestionados; la validación del proyecto en AWS EC2 se documenta con las capturas anteriores.
