# Guion del video EFT — preparación, sin grabación

Objetivo: **6 minutos**, rango permitido **5–7**. Cuatro puntos exactos; no añadir una demo extensa. Texto aproximado más pausas/transiciones; ensayar con cronómetro antes de grabar. La duración estimada no certifica un video existente.

Preparar pantalla ampliada sin .env/JWT, logs masivos ni nombres personales. Abrir resultados registrados; no ejecutar money fixtures, escalado o detenciones en vivo. Afirmaciones en pasado se refieren a las pruebas reales Etapas 4–6, no a una ejecución nueva durante el video.

## 1. Resumen ejecutivo — 00:00–00:55

**Pantalla:** readme.md y arquitectura de diagramas.md §1.

**Texto hablado:**

“Este proyecto corresponde a la Evaluación Final Transversal de Desarrollo Backend III y moderniza el banco legacy conservando su base validada. Partimos de tres procesos Spring Batch y de los canales Web, Mobile y ATM. Incorporamos los servicios Customer, Account y Payment para gestionar clientes explícitos, cuentas operacionales y solicitudes financieras.

El objetivo fue separar responsabilidades y comprobar la integridad de las operaciones, la seguridad y la recuperación ante fallos. Utilizamos PostgreSQL, OAuth2, HTTPS, configuración centralizada, Eureka, Resilience4j y Kafka. Las cuentas modernas conviven con los contratos legacy: no inferimos identidades desde nombres de archivos históricos.

La implementación se comprobó en Docker local. También demostramos dos réplicas simultáneas de cada servicio de negocio y después restauramos el entorno base. No se realizó despliegue en AWS.”

**Transición:** “Veamos qué resultados concretos permite comparar con el sistema anterior.” Cambiar al índice de evidencias; pausa breve.

## 2. Resultados y comparación con legacy — 00:55–03:00

**Pantallas:** captura histórica Batch COMPLETED, respuestas BFF finales, flujo financiero/idempotencia, Eureka y estado base. Mostrar recortes legibles, no todos los JSON completos.

**Texto hablado:**

“Los tres jobs existentes procesan transacciones diarias, intereses mensuales y movimientos anuales. Mantuvimos sus lectores, validaciones y escritura transaccional. Los tests verifican particiones, rollback de chunks y reanudación de los tres procesos. La captura que vemos pertenece a Semana 8: se presenta como antecedente, mientras la ejecución Maven actual vuelve a comprobar esas pruebas. El paralelismo es local; no afirmamos Batch distribuido ni idempotencia entre lotes nuevos.

En los canales, Web conserva un dashboard detallado, Mobile ofrece un resumen reducido y ATM presenta saldo y movimientos esenciales. Después de actualizar TLS y realizar la etapa de escala, los tres respondieron 200 y conservaron sus respuestas completas. Sin token obtuvimos 401; con un rol de otro canal, 403. Esta autorización es por cliente técnico, no por titularidad personal.

El cambio operacional principal es que ahora Customer registra clientes con UUID explícito; Account mantiene cuentas nuevas, titulares declarados y balances; Payment procesa depósitos, transferencias y pagos académicos. La transferencia debita y acredita dentro de una transacción Account. Payment conserva una clave idempotente y un comprobante: repetir la solicitud exacta devuelve el mismo resultado, mientras cambiar el importe con la misma clave produce 409.

En el run escalado quedaron veinticinco operaciones completadas, veinticinco postings, outboxes publicadas y auditorías. Los saldos terminaron en ciento uno y veinte. Son resultados de ese run, no una suma de todas las etapas.

Eureka mostró seis instancias UP con IDs e IPs diferentes, y los logs probaron atención en ambas réplicas de cada negocio. Dos consumidores Payment repartieron tres particiones Kafka, con lag final cero. Luego volvimos a una réplica por servicio de negocio y trece servicios saludables, preservando las filas anteriores y el volumen PostgreSQL.”

**Transición:** “Para llegar a esos resultados resolvimos varios problemas reales.” Mantener visible un resultado final antes de pasar al desafío.

## 3. Desafíos y soluciones — 03:00–04:55

**Pantallas:** diagrama flujo Payment, Mobile 503/recuperación, outbox coordinada y recuperación de operación pendiente. Mostrar el fallo de caché solo junto a la corrección.

**Texto hablado:**

“El primer desafío fue separar la información histórica del dominio moderno. Los CSV no permiten deducir clientes ni titulares fiables. Por eso preservamos los contratos anteriores y creamos identidades declaradas, sin migrar silenciosamente saldos o nombres.

El segundo fue la consistencia distribuida. Payment y Account no comparten una transacción de red. Si la respuesta falla, la operación puede permanecer pendiente aunque Account haya confirmado. La clave idempotente y el operationId permiten recuperar el comprobante sin mover fondos otra vez; un evento válido también reconcilia el estado.

Kafka se integra mediante outbox: el evento se confirma junto al posting, se publica después y Payment deduplica por eventId. La entrega es al menos una vez; no prometemos exactamente una publicación física. Cuando escalamos Account, fue necesario coordinar publishers con claims y lease en PostgreSQL. Los tests reales comprueban recuperación de un claim vencido y rechazo de un token antiguo.

Otro problema fue TLS: los BFF seguían sirviendo un certificado anterior. Se recrearon usando el material vigente. Para discovery por IP verificamos el DNS lógico del servicio y la cadena de confianza, sin desactivar validación.

Finalmente, el failover detectó una ruta obsoleta en Payment por una segunda caché. Deshabilitamos esa caché en el escenario escalado y habilitamos retry idempotente hacia otra instancia. La operación pendiente completó con el mismo identificador y un posting único. La prueba de resiliencia Mobile mostró 200, después 503 controlado y finalmente 200 al restaurar Account.”

**Transición:** “Estos resultados también delimitan qué falta para la entrega y qué sería trabajo futuro.”

## 4. Mejoras y próximos pasos — 04:55–06:00

**Pantallas:** evidencia Maven Etapa 7, gaps y sección cloud propuesta de despliegue.md.

**Texto hablado:**

“La verificación final reportó doscientas cincuenta y seis pruebas, sin fallos ni errores, y BUILD SUCCESS. Se habilitaron las seis pruebas PostgreSQL aisladas. Algunos tests Kafka externos heredados no se activan por defecto: la integración con el broker real está acreditada por las pruebas Docker anteriores, no por ese conteo.

Antes de entregar falta recibir la plantilla PDF y confirmar con el docente si cloud exige ejecución real o preparación. Tenemos un borrador técnico, instrucciones, matriz de rúbrica, diagramas y evidencias priorizadas. Este video y el PDF deben cerrarse con el formato exigido; la rama y el commit se publicarán únicamente al autorizarse.

La preparación cloud identifica registry, secretos gestionados, certificados, base de datos, Kafka, observabilidad, backups y migraciones coordinadas. Es una propuesta y no un despliegue AWS realizado.

Como evolución técnica, quedan la integración de los BFF con maestros modernos y autorización de personas, la identidad de los lotes Batch y la alta disponibilidad de infraestructura. Son alcances posteriores. El resultado actual es una modernización local comprobada, con operaciones trazables, recuperación controlada y escala real de tres servicios.”

**Cierre:** mantener resultado Maven/gaps visible unos segundos. No agregar promesas de cloud/SLA ni otra demo.

## Control antes de grabar

- Ensayar a ritmo natural y ajustar pausas para quedar entre 5 y 7 minutos; objetivo 6.
- Mantener exactamente los cuatro puntos anteriores, con transiciones y pantallas preparadas.
- Verificar que recortes/texto sean legibles y no revelen credenciales/tokens/PII.
- Distinguir histórico, prueba EFT real, estado final y procedimiento propuesto.
- Al grabar, añadir ubicación/URL reales y duración verificada a readme/entrega; actualmente PENDIENTE ENTREGA.
