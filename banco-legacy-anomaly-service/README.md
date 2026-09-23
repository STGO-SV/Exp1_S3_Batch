# Banco Legacy Anomaly Service

Consume `banco.transacciones.anomalas.v1` con el grupo `banco-legacy-anomaly-processors` y persiste cada anomalía en `processed_anomaly_event`.

La entrega es **at-least-once con consumidor idempotente**. El listener usa acknowledgement por registro: Kafka no considera procesado el offset hasta que finaliza la persistencia. Las restricciones sobre `event_id` y `transaction_id` convierten una redelivery en un resultado normal sin repetir el efecto. Cualquier error de base de datos distinto de una clave duplicada se propaga y detiene el contenedor, sin confirmar el registro fallido.

La concurrencia predeterminada es 1. `ANOMALY_CONSUMER_INSTANCE` permite identificar el proceso en logs y persistencia; una futura demostración horizontal utilizará tres procesos con `concurrency=1` cada uno.

Esta fase no incluye retry avanzado, DLT, transacciones Kafka+base de datos ni semántica exactly-once.
