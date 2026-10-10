# Despliegue EFT

Procedimiento de despliegue local y resultados de la ejecución en entorno AWS EC2. Incluye escalabilidad horizontal a nivel de contenedores, Eureka, failover, restauración y límites técnicos.

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

Ejecutar la secuencia de fixtures solamente en una copia de evaluación preparada sin state scale previo. Las fases flow y outbox requieren un state coherente con el escenario inicial de dos publishers; no reutilizarlo para aparentar una nueva ejecución ni borrarlo para omitir controles. ready/routing requieren dos instancias; final requiere el retorno a una.

Override elimina binding host Account con !reset, anuncia IDs/IPs únicos, verifica health por CA y configura transporte TLS/retry. Solo negocios se escalan; BFF se recrean pero no replican; --no-deps preserva infraestructura. Runner consulta Eureka, conecta a IP elegida con DNS TLS lógico y registra access logs de ambas réplicas. failover detiene una instancia y restaura en finally; no usar compose stop sobre un servicio completo replicado.

### Regreso seguro a 1+1+1

```powershell
docker compose -f docker-compose.yaml up -d --no-deps --wait --wait-timeout 240 --scale customer-service=1 --scale account-service=1 --scale payment-service=1 customer-service account-service payment-service web-bff mobile-bff atm-bff
python -B scripts/validate-eft-scale.py final
./scripts/test-eft-scale.ps1 -TestMode Verify -LogName etapa6-verify-final.log
```

Se restaura host 8085, defaults y singleton. final requiere state/snapshot de su ejecución y verifica filas previas, balances, Eureka, group/lag, BFF, HTTPS e IDs de infraestructura. Conserva fixtures del run. **Prohibido down -v, borrar volúmenes o resetear tablas.**

Resultado local registrado: seis instancias Eureka UP, routing en ambas réplicas, failover individual, publishers coordinados, dos consumidores/3 particiones/lag 0 y retorno a 13 servicios saludables. [Estado final local](docs/evidence/eft/etapa6-12-compose-final.json).

## B. Despliegue AWS EC2 ejecutado y validado

El despliegue se realizó en una única instancia AWS EC2 con Amazon Linux 2023 y Docker Compose. Las evidencias registradas corresponden a la ejecución AWS y muestran contenedores, resultados HTTP y discovery.

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
- **Payment:** durante el failover de una réplica, la captura funcional muestra depósito **HTTP 201**, `status: COMPLETED` y comprobante. Esa captura no incluye el comando de detención; se complementa con Payment-1 healthy y la recuperación conjunta.
- **Restauración:** la vista conjunta acredita nuevamente **2+2+2/16 healthy** después de las pruebas.
- **Retorno final:** al finalizar la prueba, el entorno fue restaurado a **una réplica por servicio de negocio (1+1+1)**. Las capturas disponibles muestran recuperación individual/conjunta, pero no una vista consolidada del estado final 1+1+1. No presentar `aws-2x2x2-restaurado-healthy.png` como prueba de reducción a 13 contenedores.

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
| Failover Payment | [aws-payment-service-failover-completed-201.png](docs/evidence/eft/capturas/aws-payment-service-failover-completed-201.png) | Depósito HTTP 201 con status COMPLETED; la captura no muestra el comando de detención. |
| Recuperación Account | [aws-recuperacion-failover.png](docs/evidence/eft/capturas/aws-recuperacion-failover.png) | Inicio de Account-1 y estado healthy tras Web BFF 200. |
| Recuperación Customer | [aws-customer-service-recuperacion-healthy.png](docs/evidence/eft/capturas/aws-customer-service-recuperacion-healthy.png) | Customer-1 pasa de health: starting a healthy. |
| Recuperación Payment | [aws-payment-service-recuperacion-healthy.png](docs/evidence/eft/capturas/aws-payment-service-recuperacion-healthy.png) | Payment-1 healthy. |
| Recuperación conjunta | [aws-2x2x2-restaurado-healthy.png](docs/evidence/eft/capturas/aws-2x2x2-restaurado-healthy.png) | Dos réplicas por negocio restauradas; 16 contenedores healthy. |

### Alcance de seguridad y reproducibilidad

Las requests capturadas usan `curl -k`: demuestran respuestas sobre HTTPS, **no validación de CA/hostname** en AWS. La verificación estricta TLS de Etapas 4–6 conserva su alcance local; no se extrapola a EC2. Las imágenes no muestran secretos/JWT en claro, sino referencias a variables; no trasladar sus valores a documentación.

Las capturas registran Compose, OAuth2, altas Customer/Account, dashboard, depósito, recuperación y Eureka. No contienen una secuencia completa de aprovisionamiento EC2 ni parámetros de AMI, security groups o instalación.
