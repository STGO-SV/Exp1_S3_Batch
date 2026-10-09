# Gaps reales antes de entrega

Estado vigente al 9 de octubre de 2026. La plantilla oficial PBY2203_EFT_S9_plantilla_PDF.docx fue localizada y utilizada. El informe técnico final PDF está generado y listo para revisión; existe una versión DOCX editable, según confirmación del usuario. AWS está ejecutado y documentado, con sus capturas incorporadas. El único entregable principal pendiente es el video MP4 de 5–7 minutos; después corresponde el cierre/publicación autorizado del repositorio.

| Clase | Gap | Acción / estado |
|---|---|---|
| COMPLETADO | Plantilla oficial PBY2203_EFT_S9_plantilla_PDF.docx utilizada | Informe PDF y DOCX editable generados |
| GENERADO / LISTO PARA REVISIÓN | Informe PDF final y DOCX editable | Revisar el resultado existente; no es un entregable pendiente de generación |
| BLOQUEANTE para entrega | Video sin grabar | Grabar MP4 de 5–7 min con webcam/evidencias y cuatro puntos; guion preparado, PENDIENTE ENTREGA |
| BLOQUEANTE para evaluación remota | EFT solo local, sin cierre publicado | Autorizar publicación final y registrar enlace/SHA real; no push/merge en esta etapa |
| COMPLETADO | Despliegue AWS EC2 y evidencia C6 | Amazon Linux 2023 + Compose, base 13 healthy, 2+2+2/16, Eureka y failover; [capturas](indice-evidencias-finales.md). Aprovisionamiento EC2 no reconstruido con datos ausentes |
| IMPORTANTE | CSV académicos externos al repositorio | Asegurar acceso y ruta sibling para tests; fuente indicada en instrucciones oficiales: KariVillagran/fin_legacy_data; verificar correspondencia sin cambiar datasets en esta etapa |
| IMPORTANTE | Runners ligados a state/capturas | Conservar state y respetar precondiciones; baseline sobrescribe, regression compara IDs históricos |
| IMPORTANTE: límite técnico | Batch local, relectura CSV y duplicación posible con nuevas JobInstances; anual persiste movimientos | Mantener límites explícitos frente a C3; no modificar código en corrección documental |
| IMPORTANTE: límite técnico | BFF legacy/ATM local, clientes técnicos sin IAM por titularidad, BD compartida | Explicar alcance sin inferir identidad desde nombres |
| COMPLETADO | Diagramas renderizados incorporados al informe final | Fuentes Mermaid editables conservadas |
| OPCIONAL | Benchmark productivo/HA de infraestructura | Requiere alcance posterior; no prometer SLA |

## C7 y ubicación de entregables

readme.md, instrucciones.md y despliegue.md preparados; AWS ejecutado y documentado. Informe PDF y DOCX editable generados con la plantilla oficial, listos para revisión. **C7: pendiente de cierre/publicación, no de generación del informe.**

Las instrucciones oficiales requieren los cuatro componentes (readme.md/enlace GitHub, PDF técnico, instrucciones.md y despliegue.md) **en una misma carpeta junto al video**. Raíz conserva los tres Markdown; docs/eft conserva borrador/matriz/diagramas/guion/índice. Al cerrar, reunir las piezas finales y verificar enlaces. Incluir el PDF generado y añadir el video cuando se grabe.

C8: requisitos conocidos; guion preparado; video MP4 5–7 min con webcam/evidencias y cuatro puntos **PENDIENTE ENTREGA**.

Rama eft; cierre Etapa 7: 23882c9. Registrar HEAD documental real después de los commits Etapa 7.1. Publicación no autorizada en esta corrección.

## Siguiente paso

Revisar el informe generado, grabar el video MP4 de 5–7 minutos y completar el paquete final; cerrar/publicar únicamente cuando se autorice.

## Alcance de la evidencia AWS disponible

C6 cloud ya no es un pendiente de ejecución: [base](../evidence/eft/capturas/1_despliegue_contenedores_nube.png), [escala](../evidence/eft/capturas/aws-escalabilidad-horizontal-2x2x2.png), [Eureka](../evidence/eft/capturas/aws-eureka-2x2x2-up.png) y failovers del índice. Se acredita escala de contenedores en una EC2, no HA multi-host/multi-AZ/Kubernetes. El retorno final 1+1+1 está confirmado por el usuario; añadir una captura consolidada de ese cierre sería un complemento documental. Requests con curl -k no acreditan CA/hostname verificados en AWS; no presentar las capturas como esa prueba.
