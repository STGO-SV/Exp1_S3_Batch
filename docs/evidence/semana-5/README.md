# Evidencia real — Semana 5

Estado del trabajo: rama semana-5 con cambios sin commit sobre b084f902a2f3f655bc168c8a6be349c4ce1fa580.
Las evidencias describen ese árbol de trabajo, no un nuevo commit publicado.

| Archivo | Qué demuestra | Qué no demuestra |
|---|---|---|
| build-verify.log | Extracto saneado de mvn -B clean verify; 113 tests y BUILD SUCCESS | No es log de PostgreSQL ni de NPM |
| test-results.json | 113 ejecuciones, 0 fallos, 0 errores, 0 omitidos; suites Surefire | No prueba TLS |
| verification-https-h2.json | 39 verificaciones HTTPS reales con confianza explícita; emisor y tres BFF separados | PostgreSQL real |
| bff-benchmark-https-h2.csv | 20 muestras HTTPS por endpoint, 3 calentamientos, cero errores | Rendimiento final PostgreSQL o bajo concurrencia |
| verification-internal-h2.json | Evidencia histórica HTTP anterior a TLS | TLS o PostgreSQL |
| bff-benchmark-internal-h2.csv | Evidencia histórica HTTP anterior a TLS | TLS o PostgreSQL |
| files-changed.txt | Inventario de archivos modificados/nuevos del bloque | No es un commit |
| certificate-metadata.txt | Metadatos públicos inspeccionados con keytool | No contiene clave privada ni contraseña |
| implementation-report.md | Informe técnico de 44 puntos y pendientes | No certifica PostgreSQL |

La ejecución final crea, sin sobrescribir las evidencias H2:

| Archivo | Contenido esperado |
|---|---|
| verification-https-postgres.json | Matriz HTTPS/JWT, cuenta real, contratos por canal y saldo antes/después |
| bff-benchmark-https-postgres.csv | Cinco endpoints, al menos 20 muestras, mediana, p95, bytes y errores |
| explain-analyze-postgres.txt | Planes PostgreSQL producidos dentro de una transacción READ ONLY |
| final-validation-summary.txt | Resumen compacto apto para una captura |

## Condiciones de la comprobación HTTPS

Ejecutada mediante scripts/smoke-isolated.ps1, con las aplicaciones compiladas, RSA efímero, autenticación real en
banco-legacy-auth y validación JWT real en los BFF. HTTPS localhost en 8084/8081/8082/8083; H2 en memoria, pool de una conexión.
Fixture sintético: cuenta 101, 25 movimientos y 12 anomalías. No se usaron los datasets legacy ni PostgreSQL.
Los cuatro procesos se cerraron al terminar. Los resultados no contienen tokens ni contraseñas.
El cliente fijó explícitamente `.local/tls/localhost.crt` y mantuvo validación de nombre; no usó modo skip/inseguro.
Las fechas de los CSV están en UTC; la ejecución local del 9 de septiembre aparece como 10 de septiembre UTC.

| Endpoint | Bytes | Mediana ms | p95 ms | Errores |
|---|---:|---:|---:|---:|
| Web dashboard | 2702 | 5,92 | 6,64 | 0 |
| Mobile summary | 58 | 3,94 | 4,69 | 0 |
| Mobile movements | 276 | 3,79 | 5,16 | 0 |
| ATM balance | 44 | 3,58 | 4,89 | 0 |
| ATM movements | 106 | 3,50 | 4,86 | 0 |

CSV producido con cultura es-CL: separador coma, valores decimales con coma entrecomillados.
El tamaño pequeño no garantiza menor latencia en cada muestra; observar p95 Mobile y no extrapolar con sólo 20 muestras.
Los endpoints cumplen funciones diferentes; no usar estas cifras para afirmar una comparación equivalente de pantallas completas.

## Validación PostgreSQL pendiente de ejecutar

- Ejecutar ./scripts/verify-week5-postgres.ps1 desde la sesión PowerShell 7 que contiene las variables.
- Ejecución manual de la colección Postman con el certificado confiado.

El orquestador usa confianza explícita, tokens sólo en memoria, consultas READ ONLY y no inicia Batch.
No se crearon archivos finales vacíos ni se inventaron resultados PostgreSQL.
Los logs completos quedan ignorados; build-verify.log sólo incluye líneas de resultados Maven seleccionadas, sin propiedades,
salidas de aplicaciones, tokens ni datos de cuentas. No copiar XML Surefire completos o logs privados al repositorio.
