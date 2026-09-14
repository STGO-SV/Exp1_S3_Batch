# Informe técnico final — Semana 5 HTTPS directo

Alcance verificado el 10-09-2026 sobre el árbol de trabajo local. La comprobación funcional usa H2 sintético y no PostgreSQL.

1. **Rama activa:** semana-5, siguiendo origin/semana-5.
2. **Estado Git:** árbol con cambios modificados y nuevos sin preparar; esta implementación continúa el trabajo previo de Semana 5.
3. **Aplicaciones ejecutables:** BancoLegacyBatchApplication, AuthApplication, WebBffApplication, MobileBffApplication y AtmBffApplication tienen main. Core no tiene clase Application.
4. **Servicios web:** Auth, Web, Mobile y ATM incluyen servidor web Spring Boot. Batch es una aplicación de jobs sin starter web; Core es una biblioteca.
5. **Puertos:** Auth 8084, Web 8081, Mobile 8082 y ATM 8083. Auth ya tenía 8084 explícito, estable y sin colisión, por lo que no se cambió.
6. **HTTPS en Auth:** recibe credenciales y emite tokens; cifrar sólo los BFF dejaría expuestos ambos datos en el primer tramo.
7. **Batch sin HTTPS:** no atiende solicitudes web y no forma parte de la topología de servicios de Semana 5.
8. **Core sin HTTPS:** es un módulo compartido sin proceso, puerto ni servidor web.
9. **Referencia académica:** se recibieron scripts PowerShell y Bash basados en keytool, un PKCS12 temporal y copias a tres BFF del proyecto del profesor.
10. **Adaptación de scripts:** se conservaron RSA 2048, PKCS12, alias, sujeto, SAN y vigencia; se sustituyeron los nombres por módulos reales, se añadió Auth, se parametrizó la contraseña y se evitó sobrescribir salvo Force/--force.
11. **Destinos:** Auth, Web, Mobile y ATM reciben src/main/resources/keystore.p12.
12. **Gitignore:** incluye **/src/main/resources/keystore.p12, **/*.p12 y **/*.jks.
13. **Estado de claves:** git check-ignore reconoce los cuatro keystores y la consulta de archivos trackeables no devuelve P12/JKS. No se ejecutó git add.
14. **SSL Auth:** habilitado, classpath:keystore.p12, TLS_KEYSTORE_PASSWORD con valor local changeit, PKCS12 y alias bff-local.
15. **SSL Web:** misma configuración directa, puerto 8081.
16. **SSL Mobile:** misma configuración directa, puerto 8082.
17. **SSL ATM:** misma configuración directa, puerto 8083.
18. **Alias:** bff-local.
19. **Tipo:** PKCS12 con PrivateKeyEntry y cadena autofirmada de longitud uno.
20. **Clave:** RSA de 2048 bits; firma SHA384withRSA.
21. **CN:** localhost.
22. **SAN:** DNS localhost e IP 127.0.0.1. Los puertos no pertenecen al SAN.
23. **Vigencia:** 09-09-2026 23:58:36 CLST a 09-09-2027 23:58:36 CLST, 365 días.
24. **URLs:** https://localhost:8084, https://localhost:8081, https://localhost:8082 y https://localhost:8083.
25. **Inicio Auth:** correcto en 8084 durante el smoke; la inspección adicional observó TLS 1.3 y verificación correcta con el certificado exportado.
26. **Inicio Web:** correcto en 8081 y respondió la matriz HTTPS.
27. **Inicio Mobile:** correcto en 8082 y respondió la matriz HTTPS.
28. **Inicio ATM:** correcto en 8083 y respondió la matriz HTTPS.
29. **JWT por HTTPS:** Auth emitió tokens RS256 para WEB, MOBILE y ATM; los tokens permanecieron en memoria.
30. **Matriz:** WEB produjo 200/403/403; MOBILE 403/200/403; ATM 403/403/200. Sin token produjo 401 en los tres. Hubo 39 comprobaciones y allPassed=true.
31. **Token inválido:** un token alterado produjo 401, sin confundirse con rechazo de rol.
32. **Retiro ATM:** resultado SIMULATED, sin persistir un retiro real.
33. **Tests:** 113 ejecutados, 0 fallos, 0 errores y 0 omitidos. SSL se desactiva sólo en contextos de test.
34. **Build:** el wrapper Maven clean verify terminó BUILD SUCCESS para los siete módulos el 10-09-2026 14:09:27 UTC−03.
35. **Postman:** colección y entorno usan HTTPS localhost. Se documenta verification OFF como comprobación rápida y confianza/importación como validación estricta.
36. **Benchmark:** usa HTTPS por defecto, registra trustMode y certificateValidated y separa confianza explícita del skip de laboratorio. La corrida H2 hizo 20 muestras y 3 calentamientos por endpoint, sin errores.
37. **Documentación:** README y docs/semana-5.md explican TLS directo, scripts, JWT, puertos, confianza y límites.
38. **NPM:** se conserva como alternativa experimental. No se inició, configuró ni usó.
39. **PostgreSQL pendiente:** la automatización final está preparada, pero debe ejecutarse desde la sesión PowerShell 7 del usuario, que contiene DB_URL, DB_USER y DB_PASSWORD. No se inventaron resultados.
40. **Confianza pendiente:** el sistema no confía por defecto en el certificado autofirmado. La evidencia automatizada fijó localhost.crt y mantuvo la validación de nombre; falta la ejecución manual Postman con confianza habilitada.
41. **Riesgos:** changeit y el certificado son de laboratorio; los JAR locales pueden incorporar el keystore y no deben distribuirse. JWT no aporta revocación, refresh, MFA ni rate limiting, y el rol no prueba titularidad de cuenta.
42. **Batch:** la aplicación Batch no se inició. Maven sí ejecutó sus tests aislados, parte de los 113 requeridos.
43. **Datos:** PostgreSQL y los datasets legacy no se conectaron, escribieron ni alteraron. El smoke usó H2 sintético en memoria.
44. **Git:** no hubo commit, push ni merge.

## Evidencia cuantitativa HTTPS/H2

| Endpoint | Bytes medianos | Mediana ms | p95 ms | Errores |
|---|---:|---:|---:|---:|
| Web dashboard | 2702 | 5,92 | 6,64 | 0 |
| Mobile summary | 58 | 3,94 | 4,69 | 0 |
| Mobile movements | 276 | 3,79 | 5,16 | 0 |
| ATM balance | 44 | 3,58 | 4,89 | 0 |
| ATM movements | 106 | 3,50 | 4,86 | 0 |

Los tiempos son una comprobación local, secuencial y pequeña sobre H2. No permiten inferir rendimiento de PostgreSQL ni capacidad productiva.

## Automatización de cierre PostgreSQL

scripts/verify-week5-postgres.ps1 concentra el cierre en un comando. Verifica las variables sin mostrarlas,
selecciona una cuenta real mediante SQL de sólo lectura, detecta los cuatro servicios y usa start-week5.ps1
únicamente para módulos ausentes. Renueva los JWT antes de cada bloque, valida matriz y contratos, compara el
saldo persistido antes y después del retiro SIMULATED, ejecuta el benchmark y EXPLAIN ANALYZE y termina con
clean verify. Sólo detiene procesos que él mismo inició.

El script realiza una comprobación de secretos comparando los valores sensibles en memoria con los archivos
versionables sin imprimirlos, comprueba staging vacío y los cuatro keystores ignorados. El resultado PostgreSQL
seguirá pendiente hasta obtener OVERALL: PASS en la consola del usuario.
