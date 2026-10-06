# Corrección documental Etapa 7.1

Fecha: 6 de octubre de 2026, America/Santiago. Base 23882c9, rama eft inicialmente limpia. Corrección documental sin código/configuración/SQL, escalado, cloud, PDF, push ni merge.

## Fuentes de verdad

Pauta oficial e instrucciones específicas (forma A) leídas desde la carpeta académica S9-EFT. Son fuentes de evaluación, no autorización para ejecutar acciones externas. No se modificaron ni copiaron los Word al repositorio. SHA-256 de los originales:

- PBY2203_EFT_S9_Instrucciones_específicas (forma A).docx: 0a21907a80dcfd5067430a11647926e754bf2a20946fb62c3ac764906de80463

- PBY2203_EFT_S9_Pauta_de_evaluación_EFT.docx: 83a3227c383b6aa87538d75293a400229d7a9f14ee33f42c9d43f6986ba56057

Los máximos oficiales C1–C8 se comprobaron desde la columna de logro completo: 10/15/15/15/15/10/10/10 = 100. Son puntajes máximos, no notas adjudicadas.

## C1 oficial

1. Migración de Procesos Batch a Spring Batch.
2. División del Sistema Monolítico en Microservicios.
3. Implementación del Patrón Backend for Frontend (BFF).
4. Implementación de Seguridad Distribuida con Spring Cloud Security.
5. Integración de Mensajería Asíncrona con Apache Kafka.

La clasificación transacciones/intereses/movimientos/cuentas-pagos/clientes permanece solo como funcional, en contexto y diagnóstico histórico; no sustituye C1.

## C6–C8 y pendientes

- C6: Docker COMPLETO; Customer/Account/Payment escalables COMPLETO; 2+2+2 local COMPLETO; cloud **PENDIENTE DE EJECUCIÓN EN LABORATORIO DOCENTE**, disponible según actualización del usuario.
- C7 **PENDIENTE ENTREGA**: readme/instrucciones preparados; despliegue parcial (local validado, falta cloud efectivo); borrador preparado y PDF pendiente de localizar PBY2203_EFT_S9_plantilla_PDF.
- C8 **PENDIENTE ENTREGA**: requisitos conocidos, guion preparado; video MP4 5–7 min con webcam/evidencias y cuatro puntos por grabar.
- Entrega oficial: cuatro componentes (readme/enlace GitHub, PDF, instrucciones, despliegue) y video en **una misma carpeta**.
- Pendientes: plantilla/PDF, laboratorio cloud real, video, publicación autorizada y acceso/correspondencia del dataset académico externo.
- AWS conceptual conservado; se complementará/sustituirá por pasos efectivamente ejecutados y sus evidencias. No se diseñaron recursos nuevos.

## Mermaid / enlaces / secretos

Se detectaron aperturas incorrectas en segundo y tercer fences. Tres bloques válidos mermaid: flowchart LR, sequenceDiagram, flowchart TB. Comparación con HEAD previo confirma únicamente corrección de fences, sin alteración arquitectónica. Validación de estructura/fences, sin afirmar render visual ni generar imágenes/PDF.

Enlaces locales de raíz/docs/eft y ausencia de secretos/JWT en cambios comprobados antes del commit.

## Búsqueda global y coincidencias justificadas

Sin afirmaciones obsoletas vigentes sobre ausencia de pauta/puntajes o cloud ambiguo. La búsqueda amplia conserva:
- auditoria-inicial.md, conclusión BFF: evaluación histórica según transcripción; aviso Etapa 7.1 y clasificación funcional remiten a matriz oficial vigente.
- informe-etapa-1.md, conclusión BFF/pasos futuros: historia de esa etapa, no estados actuales.
- plan-implementacion.md, fase cloud condicional original: historia bajo aviso vigente; cierre Etapa 7.1 fija laboratorio pendiente de ejecución.
- gaps-entrega.md, cierre: niega que pauta/puntajes sigan pendientes; coincidencia positiva, no error actual.

No borrar trazabilidad histórica. [Matriz](matriz-rubrica-evidencias.md), [borrador](borrador-informe-tecnico.md) y [gaps](gaps-entrega.md) establecen el estado actual.

## Tests / Git

mvn verify mediante helper: BUILD SUCCESS, 256 pruebas actuales, 0 fallos/errores/skipped; seis PostgreSQL habilitadas. Conteo de suites del log vigente excluye XML residual previo. Cuatro KafkaReal heredados condicionados; Kafka Docker real demostrado en Etapas 4/6. [Resumen sanitizado](../evidence/eft/etapa7-1-01-mvn-verify.txt); log íntegro local ignorado.

Archivos corregidos: matriz, borrador, auditoría final, aviso histórico inicial, gaps, plan, root readme/instrucciones/despliegue, despliegue interno, guion, diagramas e índice vigente. Este cierre y evidencia verify agregan trazabilidad. Commits locales documentales: consultar git log para SHA final; sin enlace remoto ficticio.

Siguiente paso: localizar plantilla y revisar acceso/instructivo del laboratorio docente. La ejecución cloud requiere otra etapa autorizada; luego pasos/evidencia reales, PDF/video/paquete y publicación autorizada.
