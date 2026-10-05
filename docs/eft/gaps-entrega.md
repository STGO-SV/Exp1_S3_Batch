# Gaps reales antes de entrega

Auditado 5 de octubre de 2026. Preparación documental completa; entrega externa no completada. Los gaps no autorizan código productivo o cloud.

| Clase | Gap | Acción y dependencia |
|---|---|---|
| BLOQUEANTE para entrega | PDF final inexistente; borrador Markdown listo | Recibir plantilla, adaptar/exportar/verificar; PENDIENTE DOCENTE y luego ENTREGA |
| BLOQUEANTE para entrega | Video sin grabar/enlace | Grabar 5–7 min con cuatro puntos y revisar; PENDIENTE ENTREGA, fuera de Etapa 7 |
| BLOQUEANTE para evaluación remota | Rama EFT solo local; enlace GitHub general no publica el cierre | Autorizar push final y registrar URL/SHA exacto; PENDIENTE ENTREGA |
| PENDIENTE DOCENTE | Si C6 exige AWS/cloud real o preparación | Obtener respuesta; si exige ejecución, acordar alcance/presupuesto/validación; no recursos creados |
| PENDIENTE DOCENTE | Plantilla PDF y pauta/puntajes oficiales | Obtenerlos y contrastar matriz; no inferir máximos |
| IMPORTANTE | CSV académicos externos al repositorio | Asegurar acceso/paquete y ruta sibling requerida por tests; confirmar permiso/formato de entrega |
| IMPORTANTE | Runners ligados a state/capturas, no demo genérica | Conservar state y revisar precondiciones; baseline sobrescribe, regression compara IDs históricos |
| IMPORTANTE: límite técnico | Batch local, relectura CSV, nuevas JobInstances pueden duplicar salidas; anual persiste movimientos | Explicitar y confirmar si pauta exige más; no rediseñar sin requisito |
| IMPORTANTE: límite técnico | BFF legacy, retiro ATM local, clientes técnicos sin titularidad IAM; BD compartida | Mantener alcance transparente; no inferir identidades desde nombres |
| OPCIONAL | Recortes legibles/diagramas exportados | Ajustar cuando haya formato PDF; Mermaid editable listo |
| OPCIONAL | Benchmark productivo/HA de infraestructura | Solo con alcance posterior; no prometer capacidad/SLA |

## Ubicación exacta

Raíz: readme.md, instrucciones.md y despliegue.md. Trabajo: docs/eft/borrador-informe-tecnico.md, matriz-rubrica-evidencias.md, diagramas.md, guion-video.md e indice-evidencias-finales.md. Evidencia: docs/evidence/eft y Batch histórico docs/evidence/semana-8.

Rama local eft; base técnica/documental Etapa 6 a31c798. Registrar HEAD después del último commit documental mediante git log; no anticipar SHA ni enlace remoto ficticio. PDF/video se ubicarán según plantilla/instrucciones docentes, aún no disponibles.

Siguiente paso: obtener las dos respuestas docentes y pauta íntegra; revisar Markdown, resolver dataset; completar PDF/video y publicar únicamente al autorizarse. Si cloud real es obligatorio, será otra etapa con recursos/evidencia reales. Si se acepta preparación, conservar esa sección explícitamente propuesta.
