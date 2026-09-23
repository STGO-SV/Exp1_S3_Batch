# Banco Legacy Anomaly Service

Consume `banco.transacciones.anomalas.v1` con el grupo `banco-legacy-anomaly-processors` y persiste cada anomalía en `processed_anomaly_event`.

La entrega es **at-least-once con consumidor idempotente**. El listener usa acknowledgement por registro: Kafka no considera procesado el offset hasta que finaliza la persistencia o hasta que el registro irrecuperable se publica correctamente en `banco.transacciones.anomalas.v1.DLT`. Las restricciones sobre `event_id` y `transaction_id` convierten una redelivery en un resultado normal sin repetir el efecto.

`DefaultErrorHandler` aplica `FixedBackOff(1000, 2)`: Spring interpreta el segundo valor como número de reintentos posteriores al intento inicial, por lo que hay exactamente **3 intentos totales**. Al agotarlos, `DeadLetterPublishingRecoverer` conserva key y payload, añade headers con los metadatos del origen y la excepción, y publica en la misma partición de la DLT. La publicación es obligatoria: si falla, el registro no se considera recuperado. `ErrorHandlingDeserializer` captura JSON inválido antes del listener; `DeserializationException` no se reintenta y pasa directamente a DLT.

La concurrencia predeterminada es 1. `ANOMALY_CONSUMER_INSTANCE` permite identificar el proceso en logs y persistencia; una futura demostración horizontal utilizará tres procesos con `concurrency=1` cada uno.

Esta fase no incluye escalabilidad horizontal, transacciones Kafka+base de datos ni semántica exactly-once.
