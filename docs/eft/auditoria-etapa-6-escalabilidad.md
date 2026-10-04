# Auditoría previa Etapa 6
Fecha 2026-10-04. Base bacd0ba, rama eft limpia. Auditoría realizada antes de cambiar código o escalar.

| Componente | Estado observado | Bloqueo / decisión mínima |
|---|---|---|
| Customer | PostgreSQL autoritativo, sin sesiones, updates con versionado; RestClient @LoadBalanced hacia Account | IDs random.uuid ya distintos; hostname compartido no identifica destino |
| Account | PostgreSQL compartido, locks FOR UPDATE ordenados, posting UNIQUE(actor,key), scheduler cada 2s | Publisher SELECT PENDING sin claim: dos JVM pueden publicar el mismo evento. Bloqueo crítico antes de escalar |
| Payment | PaymentStore PostgreSQL, UNIQUE(actor,key), auditoría PK eventId, reconciliación transaccional; sin scheduler | Mismo group financial-payment-audit; deduplicación y posting existentes reutilizables |
| Compose | Sin container_name, puertos internos 8087/8085/8086, montajes TLS read-only, depends_on health | Solo Account publica HOST 8085. Eliminar binding exclusivamente con override |
| Eureka | Customer/Payment random.uuid; Account usa identidad por defecto y hostname account-service | Unicidad explícita por HOSTNAME y anuncio IP por réplica |
| TLS/discovery | Certificado vigente tiene DNS de servicios pero no las IP efímeras de contenedores | Verificar identidad DNS lógica del servicio con verificador estándar, conectando a la IP elegida por Eureka; no regenerar PKI |
| Kafka | Topic banco.operaciones.completadas.v1 con 3 particiones, RF1, grupo Payment común | Dos consumidores deben compartir particiones; no cambiar topic |
| Infraestructura | Los 13 servicios existentes running/healthy | Mantener Postgres/volumen/Kafka/Auth/Config/Eureka singleton, no reset/down-v |

## Clientes auditados
Payment → Account, Account → Customer, Customer → Account y BFF → Account ya usan RestClient.Builder @LoadBalanced con Spring Cloud LoadBalancer/Eureka. No son URLs fijas a una réplica. Actualmente Eureka anuncia hostname DNS compartido y los destinos resultantes son indistinguibles; no se considera demostrado balanceo entre instancias por esa sola configuración.

Para routing real se anunciarán IPs únicas solo en los tres servicios escalados. Como el certificado local identifica servicios DNS y no IPs efímeras, se necesita adaptar el transporte HTTPS para validar el DNS lógico explícito del destino, preservando la validación de cadena del truststore. Se usará DefaultHostnameVerifier de Apache, sin aceptar certificados arbitrarios. Los clientes BFF requieren la misma adaptación de transporte, sin cambiar contratos ni escalarse.

## Coordinación outbox propuesta
Claim atómico PostgreSQL con FOR UPDATE SKIP LOCKED y UPDATE RETURNING, lease con reloj de DB y token de ownership, un evento por claim. La transacción del claim termina antes de esperar Kafka; ack confirmado antes de PUBLISHED. El worker falla/reintenta conservando eventId y otro worker recupera claims vencidos. La migración añade columnas y amplía el check de estado sin recrear tabla.

La semántica es at-least-once: caída tras ack y antes del update DB puede republicar el mismo eventId. Payment deduplica por eventId, por lo que no se promete exactly-once de transporte.

## Preservación
Mantener docker-compose.yaml base sin modificar. Crear docker/compose.scale.yaml; regresar al final a 1+1+1 con puertos base. Tomar snapshot READ ONLY previo de datos y comparar filas anteriores al terminar. No habilitar dos Account publishers hasta pasar pruebas de coordinación y mvn verify.
