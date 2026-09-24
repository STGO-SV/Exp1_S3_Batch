# Kafka local para Semana 7

Esta infraestructura levanta un único broker Apache Kafka 3.9.1 en modo KRaft, con el mismo nodo actuando como broker y controller. No utiliza ZooKeeper.

## Levantar y preparar Kafka

Desde la raíz del repositorio:

```powershell
.\scripts\start-week7-kafka.ps1
```

El script es idempotente: levanta Compose, espera el estado `healthy`, crea los tópicos si no existen y verifica sus metadatos. Las aplicaciones Java ejecutadas en Windows podrán conectarse a `localhost:9092`.

También se puede levantar únicamente el contenedor con:

```powershell
docker compose -f infra/kafka/compose.yaml up -d
```

## Tópicos

| Tópico | Particiones | Replication factor |
|---|---:|---:|
| `banco.transacciones.anomalas.v1` | 3 | 1 |
| `banco.transacciones.anomalas.v1.DLT` | 3 | 1 |

La creación es explícita mediante `kafka-topics.sh`; `auto.create.topics.enable` está deshabilitado.

## Comprobar el estado

```powershell
docker inspect --format '{{.State.Health.Status}}' banco-legacy-kafka
docker exec banco-legacy-kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:19092 --describe --topic banco.transacciones.anomalas.v1
docker exec banco-legacy-kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:19092 --describe --topic banco.transacciones.anomalas.v1.DLT
```

## Detener Kafka

```powershell
docker compose -f infra/kafka/compose.yaml down
```

El volumen `banco-legacy-kafka-data` conserva los datos. Para eliminarlo deliberadamente se puede agregar `--volumes` al comando `down`.

## Limitación

Esta topología local de un solo broker no representa alta disponibilidad. El replication factor 1 es apropiado únicamente para desarrollo y demostración local; una caída del broker deja Kafka temporalmente indisponible.

El publicador outbox de esta etapa se ejecuta en una sola instancia de `banco-legacy-batch`. La coordinación entre múltiples instancias publicadoras, mediante leases o bloqueos distribuidos, queda explícitamente fuera del alcance actual.
