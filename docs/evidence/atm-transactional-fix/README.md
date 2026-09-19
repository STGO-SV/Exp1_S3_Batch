# Evidencia — corrección transaccional ATM

Esta carpeta corresponde a la corrección posterior a la entrega de Semana 5 basada en retroalimentación docente.
No sustituye ni reescribe la evidencia histórica de `docs/evidence/semana-5/`.

- `validation-summary-h2.txt`: pruebas automatizadas focalizadas de persistencia, rollback y concurrencia H2.
- `build-verify.log`: resumen saneado del build completo, generado al ejecutar la validación final.
- `verification-https-postgres.json`: retiro real sobre cuenta aislada, generado sólo al ejecutar la validación PostgreSQL.
- `final-validation-summary.txt`: resumen PostgreSQL final; no se crea ni se marca PASS sin ejecución real.

`verify-week5-postgres.ps1` crea dos IDs de laboratorio que primero deben estar completamente libres. Ambos reciben un
marcador aleatorio. La limpieza exige encontrar exactamente una fila de saldo con ese marcador antes de borrar movimientos
de esos IDs y la fila propia. La cuenta funcional demuestra saldo antes, retiro, saldo después y movimiento. La segunda
cuenta ejecuta dos transacciones/conexiones concurrentes contra saldo 150 y exige un resultado COMPLETED, uno REJECTED,
saldo final 50 y un solo movimiento.

No se ejecuta Batch. PostgreSQL no se validó durante la preparación de esta corrección si faltan las variables de entorno;
en ese caso no se inventa ningún resultado.
