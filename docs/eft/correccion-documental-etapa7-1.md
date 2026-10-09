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

## C6–C8: cierre histórico y estado vigente

- C6: Docker COMPLETO; Customer/Account/Payment escalables COMPLETO; 2+2+2 local COMPLETO; cloud ejecutado y documentado en AWS EC2 según actualización vigente.
- C7 **PENDIENTE ENTREGA**: readme/instrucciones preparados; despliegue local y AWS ejecutados y documentados; borrador preparado y PDF final generado y listo para revisión, con versión DOCX editable. Actualización vigente: plantilla oficial PBY2203_EFT_S9_plantilla_PDF.docx identificada y disponible; PDF/DOCX ya generados. C7 conserva pendiente el cierre/publicación.
- C8 **PENDIENTE ENTREGA**: requisitos conocidos, guion preparado; video MP4 5–7 min con webcam/evidencias y cuatro puntos por grabar.
- Entrega oficial: cuatro componentes (readme/enlace GitHub, PDF, instrucciones, despliegue) y video en **una misma carpeta**.
- Pendientes de entrega vigentes: video MP4 de 5–7 minutos y posterior cierre/publicación autorizado. PDF/DOCX generado y AWS documentado; conservar las condiciones de reproducción de datasets.
- AWS conceptual conservado; se complementará/sustituirá por pasos efectivamente ejecutados y sus evidencias. No se diseñaron recursos nuevos.

## Mermaid / enlaces / secretos

Se detectaron aperturas incorrectas en segundo y tercer fences. Tres bloques válidos mermaid: flowchart LR, sequenceDiagram, flowchart TB. Comparación con HEAD previo confirma únicamente corrección de fences, sin alteración arquitectónica. Validación de estructura/fences, sin afirmar render visual ni generar imágenes/PDF.

Enlaces locales de raíz/docs/eft y ausencia de secretos/JWT en cambios comprobados antes del commit.

## Búsqueda global y coincidencias justificadas

Sin afirmaciones obsoletas vigentes sobre ausencia de pauta/puntajes o cloud ambiguo. La búsqueda amplia conserva:
- auditoria-inicial.md, conclusión BFF: evaluación histórica según transcripción; aviso Etapa 7.1 y clasificación funcional remiten a matriz oficial vigente.
- informe-etapa-1.md, conclusión BFF/pasos futuros: historia de esa etapa, no estados actuales.
- plan-implementacion.md, fase cloud condicional original: historia bajo aviso vigente; cierre Etapa 7.1 precedía a AWS, ya documentado en el estado vigente.
- gaps-entrega.md, cierre: niega que pauta/puntajes sigan pendientes; coincidencia positiva, no error actual.

No borrar trazabilidad histórica. [Matriz](matriz-rubrica-evidencias.md), [borrador](borrador-informe-tecnico.md) y [gaps](gaps-entrega.md) establecen el estado actual.

## Tests / Git

mvn verify mediante helper: BUILD SUCCESS, 256 pruebas actuales, 0 fallos/errores/skipped; seis PostgreSQL habilitadas. Conteo de suites del log vigente excluye XML residual previo. Cuatro KafkaReal heredados condicionados; Kafka Docker real demostrado en Etapas 4/6. [Resumen sanitizado](../evidence/eft/etapa7-1-01-mvn-verify.txt); log íntegro local ignorado.

Archivos corregidos: matriz, borrador, auditoría final, aviso histórico inicial, gaps, plan, root readme/instrucciones/despliegue, despliegue interno, guion, diagramas e índice vigente. Este cierre y evidencia verify agregan trazabilidad. Commits locales documentales: consultar git log para SHA final; sin enlace remoto ficticio.

Siguiente paso vigente: revisar el PDF/DOCX generado, grabar el video MP4 de 5–7 minutos y completar después el cierre/publicación autorizado. La plantilla ya fue utilizada; AWS ejecutado y documentado.
