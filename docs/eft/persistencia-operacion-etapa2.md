# Etapa 2 — persistencia y operación local

> Documento histórico de trazabilidad interna. Estado final (2026-10-10): desarrollo técnico completo; EFT integrada en main y publicada en GitHub. Despliegue en entorno AWS EC2 ejecutado y documentado; capturas finales incorporadas. Informe PDF generado con la plantilla oficial y DOCX editable disponible. Video grabado y entregado fuera del repositorio, sin enlace alojado en este árbol.

## Qué se aplicará

Account arranca con SQL init always y schema-locations=classpath:schema-eft-account.sql; Customer con schema-eft-customer.sql.
Los scripts crean únicamente eft_account, eft_account_holder, eft_customer e índice local.
PK/checks/FK local son compatibles con PostgreSQL; tests ejecutan los mismos SQL en H2 MODE=PostgreSQL y prueban reaplicación sin pérdida.
No se declaró prueba PostgreSQL real: motor Docker detenido durante esta etapa. No ejecutar DROP ni reinicializar volumen para incorporar tablas.

Para una base nueva Compose sigue creando tablas legacy con los scripts originales; para volumen existente los maestros se añaden al arrancar sus servicios.
Account datasource pasa de read-only a writable para su maestro. No existe nueva ruta de escritura legacy: sus GET y BFF mantienen contratos/tests originales.
Customer comparte instancia local PostgreSQL pero usa solo eft_customer; sus relaciones se consultan por REST Account.
DDL concurrente multiinstancia y roles DB por servicio se revisarán antes de etapa de escalado; esta etapa no inicia réplicas.

## Inicializador y TLS

Se añadió OAUTH_DOMAIN_CLIENT_SECRET a generación/reutilización idempotente.
Auth registra banco-domain-operator solo cuando ese secreto está configurado; scopes accounts.read/accounts.write/customers.read/customers.write.
Un .env anterior sin variable sigue siendo aceptado: cliente operador deshabilitado. No reutilizar secretos BFF para el operador.

El script comprueba SAN customer-service/payment-service. Ante certificado anterior sin esos nombres renueva únicamente archivos TLS, conservando password TLS, secretos OAuth existentes, clave JWT y password PostgreSQL. La segunda ejecución reutiliza todo.
La renovación cambia certificado TLS/truststore: reiniciar aplicaciones que ya cargaron el certificado cuando se ejecute la actualización real.
Se probó upgrade desde inicializador de 9c19005 en carpeta aislada; siete secretos/keys sin cambios y hash de .env/certificados idéntico en segunda ejecución.
NO se ejecutó el inicializador sobre .env/.local de trabajo ni se revelaron secretos.

Procedimiento futuro para entorno local, NO ejecutado ahora:
1. Revisar modelo/contratos y acceso del operador.
2. Ejecutar scripts/initialize-compose-environment.ps1 sin RotateSecrets.
3. Construir/arrancar Compose base; Customer ahora está incluido, sin puerto host.
4. Obtener token por client_credentials para banco-domain-operator con secreto externo; no pegarlo en documentación.
5. Acceder a Customer por red interna/proxy de prueba seguro, o publicar temporalmente puerto solo para laboratorio. Account conserva puerto host 8085.
6. Crear registros explícitos y verificar relaciones, 400/404/409/403/503.
7. No activar Payment ni escalar servicios en esta etapa.

## Compose

docker-compose.yaml ahora contiene 12 servicios (11 de Semana 8 más Customer).
Customer usa Config/Eureka/PostgreSQL, variables externas, truststore y HTTPS/health.
Account obtiene Customer por Eureka/nombre lógico y JWT relay; no depende del health remoto al consultar metadata local.
Customer obtiene cuentas por Account REST; no requiere dependencia Compose a Account para su propio health (evita ciclo de arranque).
Payment permanece opcional en docker/compose.eft.yaml bajo perfil eft; no ofrece negocio financiero ni se declara integrado como funcional.
Dockerfile ya incluye sus módulos desde Etapa 1.

Validación sin daemon:
    docker compose -f docker-compose.yaml config --quiet
    docker compose -f docker-compose.yaml -f docker/compose.eft.yaml -f docker/compose.scale-local.yaml --profile eft config --quiet

Ambas pasaron. No se construyeron imágenes, no se arrancaron servicios ni se hizo scale/cloud en Etapa 2.

## Persistencia inicial

No seeds productivos: abrir registro Customer/Account exige datos explícitos del operador.
No se importan nombres, saldos ni titulares contradictorios del CSV; no se crean cuentas monetarias ficticias.
El guard de Account consulta claves legacy y rechaza apertura sobre ellas con 409.
Para migrar/reutilizar esas claves: resolver titular y snapshot de saldo con revisión humana; no quitar el guard como atajo.
El cierre registral no bloquea una cuenta legacy en ATM: no se promete cierre financiero hasta resolver esa migración.