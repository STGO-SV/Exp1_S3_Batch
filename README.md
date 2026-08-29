# Banco Legacy Batch — Semana 3

Proyecto académico de **Spring Batch** desarrollado para la actividad sumativa de la Semana 3 de la asignatura Desarrollo Backend III.

El objetivo de esta entrega es optimizar la ejecución de tres procesos batch mediante particionamiento, procesamiento paralelo, tolerancia a fallos, métricas y comparación de configuraciones, utilizando los archivos de `bank_legacy_data` como fuente y PostgreSQL como base de datos de persistencia.

## Propuesta técnica

La solución utiliza una arquitectura **manager/worker con particiones**:

```text
Job
 └─ Manager Step
     ├─ Partitioner por rango
     ├─ ExecutionContext(minValue, maxValue)
     └─ Worker Steps paralelos
         └─ Reader @StepScope independiente
             → ItemProcessor
             → JdbcBatchItemWriter
             → PostgreSQL
```

Cada worker crea su propio reader. No se comparte un `FlatFileItemReader` entre threads.

Los tres Jobs se ejecutan secuencialmente, mientras que las particiones internas de cada Job se procesan en paralelo mediante un `ThreadPoolTaskExecutor`.

## Jobs implementados

### `transaccionesDiariasJob`

- Fuente: `transacciones.csv`.
- Particionamiento: rango de `id`.
- Valida y procesa cada transacción.
- Rechaza registros con datos obligatorios ausentes o inválidos.
- Persiste resultados en `transaccion_procesada`.

### `interesesMensualesJob`

- Fuente: `intereses.csv`.
- Particionamiento: rango de `cuenta_id`.
- Mantiene los registros de una misma cuenta dentro del mismo rango lógico.
- Valida los datos y aplica la transformación definida para ahorro y préstamo.
- Persiste resultados en `interes_procesado`.

### `estadosCuentaAnualesJob`

- Fuente: `cuentas_anuales.csv`.
- Particionamiento: rango de `cuenta_id`.
- Normaliza y valida los movimientos anuales.
- Persiste resultados en `movimiento_anual_procesado`.

## Estructura principal

```text
src/main/java/com/duoc/banco_legacy_batch/
├── config/       # executor y configuración de particionamiento
├── exception/    # excepciones de datos inválidos
├── job/          # Jobs, manager Steps y worker Steps
├── listener/     # métricas, skips y retries
├── model/        # modelos de entrada y salida
├── partition/    # particionamiento por rangos
├── processor/    # validaciones y transformaciones
├── reader/       # readers @StepScope por partición
└── writer/       # writers JDBC
```

## Particionamiento y paralelismo

`CsvColumnRangePartitioner` obtiene los valores mínimo y máximo desde cada CSV y construye rangos contiguos sin solapamiento.

Cada partición recibe sus límites mediante `ExecutionContext`.

Los readers de los workers son `@StepScope`, por lo que cada worker trabaja con una instancia independiente y segura para ejecución paralela.

La cantidad de worker steps depende de `gridSize`; cada worker registra de manera independiente su thread, timestamps, métricas y estado de ejecución.

## Resiliencia y tolerancia a fallos

La política distingue errores permanentes de errores transitorios:

- `InvalidBatchDataException`: skip, sin retry.
- `FlatFileParseException`: skip, sin retry.
- `TransientDataAccessException`: hasta 3 intentos.
- Errores no clasificados: provocan el fallo del worker y del Job.

Los listeners registran, entre otros datos:

- inicio, fin, estado y duración;
- configuración de grid, threads y chunk;
- particiones creadas y fallidas;
- thread utilizado por cada worker;
- `readCount`, `writeCount` y `filterCount`;
- skips separados por etapa;
- `retryCount`.

Las métricas finales se agregan desde los worker steps, evitando sumar nuevamente los conteos del manager step.

Las principales relaciones de reconciliación son:

```text
inputCount = readCount + readSkipCount

totalSkipCount =
    readSkipCount
  + processSkipCount
  + writeSkipCount

inputCount =
    writeCount
  + totalSkipCount
  + filterCount
```

## Resultado funcional de Semana 3

Cada archivo de Semana 3 contiene 1000 filas.

| Job | Input | Written | Skipped | Estado |
|---|---:|---:|---:|---|
| Transacciones | 1000 | 401 | 599 | COMPLETED |
| Intereses | 1000 | 282 | 718 | COMPLETED |
| Estado anual | 1000 | 642 | 358 | COMPLETED |
| **Total** | **3000** | **1325** | **1675** | **COMPLETED** |

También se verificó que los **401 `transaccion_id` persistidos son distintos**, evitando duplicados producidos por el procesamiento paralelo.

## Configuración de escalado

La aplicación permite parametrizar el escalamiento mediante variables de entorno:

| Variable | Uso |
|---|---|
| `BATCH_GRID_SIZE` | cantidad de particiones |
| `BATCH_THREAD_COUNT` | cantidad máxima de threads |
| `BATCH_CHUNK_SIZE` | tamaño de chunk |
| `BATCH_SKIP_LIMIT` | límite de skips |
| `BATCH_INPUT_DIR` | directorio de entrada |

Para comparar el efecto del paralelismo se mantuvo:

```text
chunkSize = 100
```

y se evaluaron las configuraciones:

```text
1×1
2×2
4×4
8×8
```

## Benchmark de configuraciones

`PartitionBenchmarkTests` ejecuta los tres Jobs sobre H2 aislado para mantener un entorno reproducible.

Cada ejecución del benchmark realiza tres iteraciones internas por configuración y reporta la mediana de esas tres iteraciones.

Para reducir la variabilidad del entorno, el benchmark completo se ejecutó cinco veces de forma independiente. Por tanto, cada valor T1–T5 de la tabla corresponde a la mediana de tres iteraciones.

En total, cada configuración fue observada en 15 mediciones internas.

| Grid | Threads | T1 | T2 | T3 | T4 | T5 | Promedio | Mediana |
|---:|---:|---:|---:|---:|---:|---:|---:|---:|
| 1 | 1 | 449 | 502 | 430 | 426 | 449 | 451,2 ms | 449 ms |
| 2 | 2 | 209 | 216 | 215 | 297 | 286 | 244,6 ms | 216 ms |
| 4 | 4 | 131 | 149 | 147 | 132 | 193 | 150,4 ms | 147 ms |
| 8 | 8 | 118 | 131 | 194 | 147 | 144 | **146,8 ms** | **144 ms** |

La configuración seleccionada para esta entrega es:

```text
gridSize = 8
threads = 8
chunkSize = 100
```

La configuración **8×8** obtuvo el menor tiempo promedio y la menor mediana entre las alternativas evaluadas.

Los tiempos dependen del hardware y de la carga del sistema, por lo que esta conclusión corresponde al entorno de prueba utilizado y no pretende establecer un óptimo universal.

## Requisitos

- Java 21
- Maven 3.x o Maven Wrapper incluido
- PostgreSQL
- Base de datos `banco_legacy_batch`
- Dataset `bank_legacy_data`

## Configuración de PostgreSQL

Las credenciales se definieron mediante variables de entorno:

```powershell
$env:DB_URL = 'jdbc:postgresql://localhost:5432/banco_legacy_batch'
$env:DB_USER = 'tu_usuario'
$env:DB_PASSWORD = 'tu_clave'
```

No se almacenan credenciales reales en el repositorio.

## Configuración recomendada

```powershell
$env:BATCH_INPUT_DIR = 'C:/ruta/bank_legacy_data/data/semana_3'
$env:BATCH_GRID_SIZE = '8'
$env:BATCH_THREAD_COUNT = '8'
$env:BATCH_CHUNK_SIZE = '100'
$env:BATCH_SKIP_LIMIT = '1000'
```

## Ejecución

Con Maven Wrapper:

```powershell
.\mvnw.cmd spring-boot:run
```

Con Maven instalado globalmente:

```powershell
mvn spring-boot:run
```

La ejecución debe finalizar los tres Jobs con estado `COMPLETED`.

## Tests

Suite completa:

```powershell
mvn clean test
```

Resultado verificado:

```text
Tests run: 11
Failures: 0
Errors: 0
Skipped: 0
BUILD SUCCESS
```

La suite verifica:

- ejecución funcional de los tres Jobs;
- particiones contiguas y sin solapamiento;
- readers limitados a su rango;
- workers en threads `batch-partition-*`;
- reconciliación de las 3000 entradas;
- ausencia de duplicados por paralelismo;
- skips y estados finales;
- retry recuperable y retry agotado;
- comparación 1×1, 2×2, 4×4 y 8×8.

## Ejecución aislada del benchmark

```powershell
mvn "-Dtest=PartitionBenchmarkTests" test
```

El benchmark genera:

```text
target/benchmark-results.csv
```

## Dataset de tests

La ubicación de los datasets puede configurarse sin modificar código.

Raíz común:

```powershell
mvn clean test "-Dbatch.test-data-root=C:/ruta/bank_legacy_data/data"
```

Sólo Semana 3:

```powershell
mvn clean test "-Dbatch.test-input-directory=C:/ruta/bank_legacy_data/data/semana_3"
```

También pueden utilizarse:

```text
BATCH_TEST_DATA_ROOT
BATCH_TEST_INPUT_DIR
```

## Evidencia de ejecución

La entrega incorpora evidencias de:

1. los tres Jobs con estado `COMPLETED`;
2. manager steps y worker partitions;
3. ejecución concurrente;
4. reconciliación de los 3000 registros;
5. persistencia en PostgreSQL;
6. ausencia de duplicados;
7. suite de 11 tests con `BUILD SUCCESS`;
8. retry recuperable y retry agotado;
9. comparación de configuraciones de escalamiento.

## Verificaciones SQL principales

Conteos esperados sobre tablas limpias tras una única ejecución:

```sql
SELECT COUNT(*) FROM transaccion_procesada;       -- 401
SELECT COUNT(*) FROM interes_procesado;           -- 282
SELECT COUNT(*) FROM movimiento_anual_procesado;  -- 642
```

Ausencia de duplicados de transacciones:

```sql
SELECT
    COUNT(*) AS total_registros,
    COUNT(DISTINCT transaccion_id) AS ids_distintos
FROM transaccion_procesada;
```

Resultado esperado:

```text
total_registros = 401
ids_distintos   = 401
```

La metadata de Spring Batch queda disponible en `batch_job_execution` y `batch_step_execution` para comprobar estados, tiempos, métricas y particiones.

## Nota sobre reejecución

Cada ejecución utiliza nuevos parámetros de Job y vuelve a insertar los resultados procesados.

Para obtener conteos exactos de una ejecución, use tablas de salida limpias o compare los conteos antes y después.

No se eliminan datos automáticamente.
