# Proxy local alternativo/experimental

La solución efectiva de Semana 5 usa TLS directo en Auth y los tres BFF mediante `server.ssl.*` y los scripts oficiales
adaptados. Esta infraestructura se conserva como preparación histórica y no debe iniciarse ni presentarse como topología final.

Imagen del proyecto Nginx Proxy Manager fijada a 2.15.1; SQLite interna, sin otro servidor de base de datos.
Referencia: https://nginxproxymanager.com/setup/

Desde la raíz: `docker compose -f infra/nginx-proxy-manager/compose.yaml up -d`.
Abrir http://localhost:81 y completar el alta/cambio de credenciales administrativas en la interfaz.
Los tres puertos publicados están limitados a loopback. Los volúmenes contienen configuración y material privado:
no exportarlos ni subirlos a Git. `docker compose ... stop` conserva esa configuración.

## Resolución y rutas

Agregar manualmente, con permisos administrativos, a
`C:\Windows\System32\drivers\etc\hosts`:

```text
127.0.0.1 web.banco.local mobile.banco.local atm.banco.local
```

Crear tres Proxy Hosts en NPM (Forward Scheme: http):

| Domain | Forward Hostname | Forward Port |
|---|---|---:|
| web.banco.local | host.docker.internal | 8081 |
| mobile.banco.local | host.docker.internal | 8082 |
| atm.banco.local | host.docker.internal | 8083 |

No agregar Access List de Basic: los BFF comprueban Bearer. NPM debe conservar Authorization.
No se requiere websocket ni modificar rutas. Guardar los hosts inicialmente sin SSL si todavía no hay certificado.
Los BFF pueden ejecutarse desde IntelliJ y deben ser alcanzables desde Docker Desktop.
No abrir 8081–8083 a redes públicas; mantener las reglas de Windows Firewall restringidas al entorno local.
El puerto y sus reglas no se modifican automáticamente por este proyecto.

## Certificado personalizado — pendiente del profesor

No se ha generado ningún certificado TLS. El script oficial debe producir certificados con SAN para los
tres nombres (o uno por host), clave privada correspondiente y cadena pública/CA, según su procedimiento.
Guardar material privado fuera del repositorio o en `.local/` (ignorado).

En NPM: SSL Certificates → Add SSL Certificate → Custom; cargar Certificate Key,
Certificate y cadena intermedia si corresponde. En cada Proxy Host seleccionar el certificado y Force SSL.
No habilitar HSTS hasta comprobar nombres, confianza y acceso; evitar quedar bloqueado en una demo local.

Postman: usar https://web.banco.local, https://mobile.banco.local y https://atm.banco.local.
Desactivar verificación sólo sirve como diagnóstico. Para evidencia final, importar la CA/certificado público
en la confianza de Postman según el script entregado, activar verificación y guardar el resultado.
Los scripts de verificación no permiten omitir la validación TLS.

El emisor permanece en http://127.0.0.1:8084 para autenticación exclusivamente local.
Si se necesita acceder al emisor desde otra máquina, preparar un cuarto host TLS y su certificado;
no cambiar AUTH_ADDRESS a 0.0.0.0 para exponer credenciales por HTTP.

Compose prepara el servicio; los Proxy Hosts y la importación custom se configuran mediante la interfaz y
persisten en los volúmenes. No se afirma que Compose aprovisione esos objetos automáticamente.
