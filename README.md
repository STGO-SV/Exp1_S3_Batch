# Banco Legacy Batch — Semana 3

Proyecto académico de Spring Batch para modernizar tres procesos legacy ficticios del Banco XYZ. La solución conserva una lógica bancaria deliberadamente simple y concentra la complejidad en particionamiento, paralelismo, resiliencia y observabilidad.

## Arquitectura

Cada Job sigue este flujo:

```text
Job
 └─ Manager Step
     ├─ Partitioner por rango
     ├─ ExecutionContext(minValue, maxValue)
     └─ Worker Steps paralelos
         └─ Reader @StepScope independiente
             → Processor
             → Writer JDBC
             → PostgreSQL
```

No se comparte `FlatFileItemReader` entre threads. Cada worker crea su propio reader y filtra exclusivamente el rango lógico asignado. Los Jobs se lanzan secuencialmente, pero las particiones de cada Job se ejecutan en paralelo mediante un `ThreadPoolTaskExecutor` explícito.

## Jobs y particionamiento

- `transaccionesDiariasJob`: particiona `transacciones.csv` por rango de `id`; acepta montos positivos y tipos `debito`/`credito`, y marca como anomalía académica un monto superior a 2000.
- `interesesMensualesJob`: particiona `intereses.csv` por `cuenta_id`, por lo que todos los registros de una cuenta quedan en el mismo worker; aplica tasas académicas de 1% para `ahorro` y 2% para `prestamo`.
- `estadosCuentaAnualesJob`: particiona `cuentas_anuales.csv` por `cuenta_id`; normaliza tipo y descripción y acepta `deposito`, `retiro` y `compra`.

El `CsvColumnRangePartitioner` obtiene los límites reales desde cada CSV y crea rangos contiguos, completos y sin solapamientos. Los readers aceptan fechas estrictas en formatos `yyyy-MM-dd`, `dd-MM-yyyy`, `dd/MM/yyyy` y `yyyy/MM/dd`.

## Estructura principal

```text
src/main/java/com/duoc/banco_legacy_batch/
├── config/       # executor y partitioners
├── exception/    # errores deterministas de datos
├── job/          # Jobs, manager Steps y worker Steps
├── listener/     # métricas, skip y retry
├── model/        # entradas y resultados procesados
├── partition/    # particionamiento por rangos CSV
├── processor/    # validaciones y transformaciones
├── reader/       # readers @StepScope por partición
└── writer/       # writers JDBC
```

## Configuración de escalado

| Variable | Propiedad | Predeterminado |
|---|---|---:|
| `BATCH_GRID_SIZE` | `batch.partition.grid-size` | 4 |
| `BATCH_THREAD_COUNT` | `batch.partition.thread-count` | 4 |
| `BATCH_CHUNK_SIZE` | `batch.chunk-size` | 100 |
| `BATCH_SKIP_LIMIT` | `batch.skip-limit` | 1000 |
| `BATCH_INPUT_DIR` | `batch.input-directory` | `C:/Dev/Duoc/DBE3/bank_legacy_data/data/semana_3` |

El límite de skip es parametrizable porque los CSV de Semana 3 contienen cientos de casos inválidos intencionales. Semana 1 continúa probándose con límite 10.

## Resiliencia y observabilidad

- `InvalidBatchDataException` y `FlatFileParseException`: skip determinista, sin retry.
- `TransientDataAccessException`: máximo 3 intentos.
- Cualquier error no clasificado: fallo del worker, manager y Job.

Los listeners registran campos `clave=valor` para facilitar evidencia:

- inicio, fin, estado y duración del Job;
- configuración grid/threads/chunk;
- cantidad de particiones y particiones fallidas;
- thread, duración, read/write/filter/skip/retry por worker;
- item, etapa y motivo de cada skip;
- intento y excepción de cada retry.

Los tests simulan errores transitorios sólo mediante writers de prueba: verifican recuperación en el segundo intento y fallo después de agotar tres intentos.

## Resultado funcional de Semana 3

Los tres archivos contienen 1000 filas. La reconciliación verificada es:

| Job | Input | Written | Skipped | Estado |
|---|---:|---:|---:|---|
| Transacciones | 1000 | 401 | 599 | COMPLETED |
| Intereses | 1000 | 282 | 718 | COMPLETED |
| Estado anual | 1000 | 642 | 358 | COMPLETED |
| **Total** | **3000** | **1325** | **1675** | **COMPLETED** |

Se verifica también que los 401 `transaccion_id` persistidos sean distintos, evitando duplicados causados por particionamiento.

## Benchmark

`PartitionBenchmarkTests` ejecuta los tres Jobs en H2 aislado, realiza tres iteraciones por configuración y registra la mediana en `target/benchmark-results.csv`. La evidencia consolidada está en [`docs/benchmark-results.csv`](docs/benchmark-results.csv).

Resultados medidos en este equipo, con chunk 100:

| Grid | Threads | Mediana total | Resultado |
|---:|---:|---:|---|
| 1 | 1 | 389 ms | COMPLETED |
| 2 | 2 | 200 ms | COMPLETED |
| 4 | 4 | 126 ms | COMPLETED |
| 8 | 8 | 130 ms | COMPLETED |

La configuración elegida es **grid 4, threads 4, chunk 100**. Fue la más rápida de la medición final y evita duplicar recursos para una configuración 8×8 que no mejoró el tiempo. Los tiempos dependen del hardware; se deben regenerar como evidencia en el equipo evaluador.

## PostgreSQL

| Variable | Valor predeterminado |
|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/banco_legacy_batch` |
| `DB_USER` | `postgres` |
| `DB_PASSWORD` | `postgres` |

La base y las credenciales deben existir antes del inicio. No se almacenan credenciales reales. Spring crea las tablas Batch y las tablas de resultados mediante `schema.sql`.

```powershell
$env:DB_URL = 'jdbc:postgresql://localhost:5432/banco_legacy_batch'
$env:DB_USER = 'tu_usuario'
$env:DB_PASSWORD = 'tu_clave'
$env:BATCH_INPUT_DIR = 'C:/Dev/Duoc/DBE3/bank_legacy_data/data/semana_3'
$env:BATCH_GRID_SIZE = '4'
$env:BATCH_THREAD_COUNT = '4'
$env:BATCH_CHUNK_SIZE = '100'
$env:BATCH_SKIP_LIMIT = '1000'
.\mvnw.cmd spring-boot:run
```

Cada inicio utiliza parámetros nuevos y vuelve a insertar resultados. Para una evidencia con conteos exactos debe utilizarse una base vacía o comparar los conteos antes y después; no se eliminan datos automáticamente.

## Tests y benchmark reproducible

```powershell
.\mvnw.cmd clean test
```

La suite verifica:

- regresión completa de Semana 1;
- límites contiguos de partición;
- reader limitado a su rango;
- workers en threads `batch-partition-*`;
- cobertura y reconciliación de 3000 entradas;
- ausencia de duplicados por paralelismo;
- skips y estados finales;
- retry recuperable y retry agotado;
- parametrización y benchmark 1×1, 2×2, 4×4 y 8×8.

## Consultas de evidencia

```sql
SELECT COUNT(*), COUNT(DISTINCT transaccion_id) FROM transaccion_procesada;
SELECT COUNT(*) FROM interes_procesado;
SELECT COUNT(*) FROM movimiento_anual_procesado;

SELECT job_execution_id, status, start_time, end_time
FROM batch_job_execution ORDER BY job_execution_id DESC;

SELECT step_name, status, read_count, write_count,
       read_skip_count, process_skip_count, write_skip_count
FROM batch_step_execution
ORDER BY step_execution_id DESC;
```
