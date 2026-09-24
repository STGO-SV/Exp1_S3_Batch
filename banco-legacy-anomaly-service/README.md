# Banco Legacy Anomaly Service

Consume `banco.transacciones.anomalas.v1` con el grupo `banco-legacy-anomaly-processors` y persiste cada anomalía en `processed_anomaly_event`.

La entrega es **at-least-once con consumidor idempotente**. El listener usa acknowledgement por registro: Kafka no considera procesado el offset hasta que finaliza la persistencia o hasta que el registro irrecuperable se publica correctamente en `banco.transacciones.anomalas.v1.DLT`. Las restricciones sobre `event_id` y `transaction_id` convierten una redelivery en un resultado normal sin repetir el efecto.

`DefaultErrorHandler` aplica `FixedBackOff(1000, 2)`: Spring interpreta el segundo valor como número de reintentos posteriores al intento inicial, por lo que hay exactamente **3 intentos totales**. Al agotarlos, `DeadLetterPublishingRecoverer` conserva key y payload, añade headers con los metadatos del origen y la excepción, y publica en la misma partición de la DLT. La publicación es obligatoria: si falla, el registro no se considera recuperado. `ErrorHandlingDeserializer` captura JSON inválido antes del listener; `DeserializationException` no se reintenta y pasa directamente a DLT.

La concurrencia predeterminada es 1. `ANOMALY_CONSUMER_INSTANCE` permite identificar el proceso en logs y persistencia; una futura demostración horizontal utilizará tres procesos con `concurrency=1` cada uno.

La demostración horizontal conserva `concurrency=1` por proceso y no incorpora Kubernetes, coordinación distribuida propia, transacciones Kafka+base de datos ni semántica exactly-once.

## Demostración horizontal

Con el JAR empaquetado y Kafka disponible, `.\scripts\start-week7-consumers.ps1` inicia tres JVM independientes (`consumer-1`, `consumer-2` y `consumer-3`) en el mismo consumer group y con `concurrency=1` por proceso. Los PID y logs separados se guardan bajo `target/week7-consumers`. `.\scripts\stop-week7-consumers.ps1` detiene exclusivamente los procesos registrados por el script, validando PID, ejecutable y hora de inicio.

La asignación se comprueba con `kafka-consumer-groups.sh --bootstrap-server localhost:9092 --group banco-legacy-anomaly-processors --describe`. El resumen persistido para una ejecución se obtiene ejecutando `scripts/Week7ScalingEvidence.java` con su `correlationId` y el driver PostgreSQL del repositorio Maven en el classpath.
