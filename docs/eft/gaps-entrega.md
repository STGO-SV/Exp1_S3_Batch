# Gaps reales antes de entrega

Actualizado contra pauta e instrucciones oficiales el 6 de octubre de 2026, Etapa 7.1. **Pauta y puntajes conocidos: total máximo 100.** La preparación documental no constituye entrega final ni ejecución cloud.

| Clase | Gap | Acción / estado |
|---|---|---|
| BLOQUEANTE para PDF | Plantilla PBY2203_EFT_S9_plantilla_PDF pendiente de localizar | Localizar en AVA; no inventar plantilla ni generar PDF antes |
| BLOQUEANTE para entrega | Informe PDF final todavía inexistente; borrador preparado | Adaptar/exportar/verificar tras disponer de plantilla; PENDIENTE ENTREGA |
| BLOQUEANTE para entrega | Video sin grabar | Grabar MP4 de 5–7 min con webcam/evidencias y cuatro puntos; guion preparado, PENDIENTE ENTREGA |
| BLOQUEANTE para evaluación remota | EFT solo local, sin cierre publicado | Autorizar publicación final y registrar enlace/SHA real; no push/merge en esta etapa |
| IMPORTANTE / PENDIENTE DE EJECUCIÓN | Laboratorio docente disponible para despliegue real de contenedores | Ejecutar en etapa autorizada; capturar evidencia y complementar/sustituir preparación conceptual en despliegue.md |
| IMPORTANTE | despliegue.md preparado parcialmente | Parte local validada; falta procedimiento cloud real del laboratorio después de ejecutarlo |
| IMPORTANTE | CSV académicos externos al repositorio | Asegurar acceso y ruta sibling para tests; fuente indicada en instrucciones oficiales: KariVillagran/fin_legacy_data; verificar correspondencia sin cambiar datasets en esta etapa |
| IMPORTANTE | Runners ligados a state/capturas | Conservar state y respetar precondiciones; baseline sobrescribe, regression compara IDs históricos |
| IMPORTANTE: límite técnico | Batch local, relectura CSV y duplicación posible con nuevas JobInstances; anual persiste movimientos | Mantener límites explícitos frente a C3; no modificar código en corrección documental |
| IMPORTANTE: límite técnico | BFF legacy/ATM local, clientes técnicos sin IAM por titularidad, BD compartida | Explicar alcance sin inferir identidad desde nombres |
| OPCIONAL | Exportación legible de diagramas/recortes | Ajustar al formato de plantilla; fuente Mermaid corregida |
| OPCIONAL | Benchmark productivo/HA de infraestructura | Requiere alcance posterior; no prometer SLA |

## C7 y ubicación de entregables

readme.md: preparado. instrucciones.md: preparado. despliegue.md: preparado parcialmente; pendiente procedimiento cloud real. Informe técnico: borrador preparado, PDF pendiente de plantilla. **C7: PENDIENTE ENTREGA**.

Las instrucciones oficiales requieren los cuatro componentes (readme.md/enlace GitHub, PDF técnico, instrucciones.md y despliegue.md) **en una misma carpeta junto al video**. Raíz conserva los tres Markdown; docs/eft conserva borrador/matriz/diagramas/guion/índice. Al cerrar, reunir las piezas finales y verificar enlaces. No crear ahora un paquete con PDF/video ficticios.

C8: requisitos conocidos; guion preparado; video MP4 5–7 min con webcam/evidencias y cuatro puntos **PENDIENTE ENTREGA**.

Rama eft; cierre Etapa 7: 23882c9. Registrar HEAD documental real después de los commits Etapa 7.1. Publicación no autorizada en esta corrección.

## Siguiente paso

Localizar plantilla oficial y revisar el acceso/instructivo del laboratorio docente. Ejecutar cloud en otra etapa autorizada, registrar procedimiento/evidencias reales; completar PDF/video y paquete único; publicar solamente cuando se autorice. Pauta/puntajes ya no son pendientes.
