# Uso de IA y referencias de diseño

## Alcance

La IA se utilizó como apoyo para:

- inspeccionar el repositorio y el contrato existente;
- generar flujo de desarrollo basico;
- documentación de swagger en los router;
- generar test unitarios y pruebas automatizadas;
- Documentación del proyecto;
- reconciliar la documentación con el código final.
- generación de diagramas de flujo y arquitectura del proyecto.
- generación de mensajes de validación y errores en texto para optimización de tareas repetitivas.
- revición de querys de base de datos consistentes con lo que se requiere.

========================================

## Decisiones y sugerencias aceptadas

- codigo generado por la IA apartir de specs refinados por mi.
- Test generados durante el desarrollo y que cumplen con los criterios de calidad y cobertura de pruebas.

## correcciones realizadas
- correcciones en la implementación de la arquitectura.
- correcciones en la implementación de la logica de negocio para que fuese externa a las entidades de dominio.
- correcciones en el cierre de contratos (interfaces)
- correecciones en el mapeo de entidades de dominio para evitar ser usados en la capa de aplicación.
- corrección en la ubicación del adapter transaction
- 