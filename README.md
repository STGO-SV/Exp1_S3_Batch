# Banco Legacy Batch — Semana 1

Línea base secuencial de Spring Batch para procesar los CSV académicos de la Semana 1.

## Procesos

- `transaccionesDiariasJob`: acepta montos positivos y tipos `debito`/`credito`. Marca como anomalía académica un monto superior a 2000.
- `interesesMensualesJob`: acepta `ahorro` y `prestamo`. Aplica respectivamente tasas convencionales de 1% y 2% al saldo; no representa una regla financiera real.
- `estadosCuentaAnualesJob`: normaliza tipo y descripción, acepta `deposito`, `retiro` y `compra`, y descarta movimientos de monto cero.

Los procesadores lanzan una excepción de validación para registros que no cumplen las reglas. Cada Step permite omitir hasta 10 errores de datos o líneas que no puedan parsearse; los errores fatales de escritura en base de datos no se ocultan.

## Resiliencia y observabilidad

Los tres Steps conservan la ejecución secuencial y aplican estas políticas explícitas:

- errores deterministas de validación (`InvalidBatchDataException`) o parsing (`FlatFileParseException`): `skip`, límite 10;
- errores transitorios de acceso a datos (`TransientDataAccessException`): `retry`, máximo 3 intentos;
- cualquier error no clasificado: el Step y el Job fallan.

Los rechazos de negocio se registran como `processSkipCount`; no se reintentan. Los listeners generan logs estructurados mediante pares `clave=valor`:

- `BatchJobMetricsListener`: inicio, fin, estado, duración y fallos del Job;
- `BatchStepMetricsListener`: lecturas, escrituras, filtros, skips por etapa, retries, fallos y duración;
- `BatchSkipLoggingListener`: item, etapa, excepción y motivo de cada skip;
- `RetryMetricsListener`: intento, Step, excepción y motivo de cada retry transitorio.

El retry se demuestra mediante writers simulados exclusivamente en tests: uno se recupera en el segundo intento y otro falla después de agotar tres intentos. La lógica productiva no contiene fallos artificiales.

## PostgreSQL

La aplicación usa estos valores predeterminados, todos reemplazables mediante variables de entorno:

| Variable | Valor predeterminado |
|---|---|
| `DB_URL` | `jdbc:postgresql://localhost:5432/banco_legacy_batch` |
| `DB_USER` | `postgres` |
| `DB_PASSWORD` | `postgres` |
| `BATCH_INPUT_DIR` | `C:/Dev/Duoc/DBE3/bank_legacy_data/data/semana_1` |

La base de datos y las credenciales deben existir antes de iniciar la aplicación. Spring crea las tablas de metadata Batch y las tres tablas de resultados mediante `schema.sql`.

En PowerShell, si los valores predeterminados no corresponden a tu instalación:

```powershell
$env:DB_URL = 'jdbc:postgresql://localhost:5432/banco_legacy_batch'
$env:DB_USER = 'tu_usuario'
$env:DB_PASSWORD = 'tu_clave'
$env:BATCH_INPUT_DIR = 'C:/Dev/Duoc/DBE3/bank_legacy_data/data/semana_1'
.\mvnw.cmd spring-boot:run
```

Al iniciar, los tres Jobs se ejecutan secuencialmente. Cada ejecución usa un parámetro temporal distinto, por lo que una nueva ejecución vuelve a insertar los resultados.

## Pruebas

```powershell
.\mvnw.cmd clean test
```

Las pruebas usan H2 en memoria, ejecutan los tres Jobs con los CSV reales y verifican:

- 8 transacciones procesadas;
- 7 intereses procesados;
- 8 movimientos anuales procesados;
- 1 transacción marcada como anomalía;
- saldo procesado `5050.00` para la cuenta 101.
- skips de validación y sus contadores;
- ausencia de retry para errores permanentes de datos;
- recuperación tras una excepción transitoria;
- fallo correcto al superar el límite de retry.

## Consultas de comprobación

```sql
SELECT * FROM transaccion_procesada ORDER BY transaccion_id;
SELECT * FROM interes_procesado ORDER BY cuenta_id;
SELECT * FROM movimiento_anual_procesado ORDER BY cuenta_id, fecha;

SELECT job_instance_id, job_name FROM batch_job_instance ORDER BY job_instance_id;
SELECT job_execution_id, status, start_time, end_time FROM batch_job_execution ORDER BY job_execution_id;
```
