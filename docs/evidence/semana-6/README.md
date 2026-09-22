# Evidencia Semana 6 — cadena piloto Mobile

Esta carpeta recibirá evidencia saneada de Config Server, Eureka, descubrimiento, JWT y Circuit Breaker.
No se deben guardar tokens, claves, contraseñas, keystores ni payloads bancarios completos.

Evidencia automatizada disponible en esta fase:

- Config Server sirve configuración native en tests.
- Mobile obtiene una propiedad remota desde un servidor de configuración HTTP de prueba.
- Eureka Server inicia en modo standalone.
- Account Service consulta H2 y exige JWT.
- Mobile reenvía Bearer y conserva 200/404/503.
- El fallback 503 no inventa saldos ni movimientos.
- El Circuit Breaker abre tras fallos y cierra al superar la llamada de prueba posterior a HALF_OPEN.
- Las regresiones Semana 5 se validan mediante el build completo.

La validación manual en vivo informó Config Server y Eureka PASS, Mobile 200 con circuito CLOSED, fallback 503
al detener Account Service, estado HALF_OPEN después de OPEN y recuperación 200/CLOSED al reiniciarlo. Este relato
no sustituye los archivos generados por el verificador automatizado.

`scripts/verify-week6.ps1` comprueba el estado sano. `scripts/verify-week6-resilience.ps1` crea, sólo cuando se
ejecuta, un subdirectorio `live/AAAAmmdd-HHMMSS/` con JSON saneados para cada fase: `01-healthy`, `03-fallback`,
`04-open`, `05-half-open`, `08-recovery`, `09-closed` y un resultado global. Los archivos contienen estados, códigos
HTTP y fecha de observación; no contienen tokens, contraseñas ni balances. No se inventan resultados de una
ejecución automatizada que todavía no se ha realizado.
