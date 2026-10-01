# Contexto de implementación: core de créditos

Especificación resumida. `ARQUITECTURA.md` contiene las decisiones completas y sus fuentes. Estado: diseño; no asumir que existen código, tablas o contenedores implementados. No modelar la base de datos ni añadir funcionalidades sin una solicitud posterior.

## Stack y límites

Backend único: Java, Spring Boot WebFlux, router y handler, Spring Data R2DBC, PostgreSQL y `TransactionalOperator`. Clases con Lombok; sin `record`, `@Valid`, validaciones automáticas de DTO, JPA ni controladores anotados. Validar manualmente.

Código en inglés: clases, interfaces, métodos, campos, enums, paquetes, comentarios y pruebas. Solo los mensajes destinados al cliente se escriben en español. Conservar la estructura actual de puertos `domain/port/portin` y `domain/port/portout`.

Mappers totalmente manuales, con métodos estáticos y construcción mediante builders. `ApplicationDtoMapper.toDomain(ApplicationRequestDto)` convierte a `ApplicationData`; `toResponse(CreditApplication)` construye `ApplicationResponseDto`. Request: `amount` como `BigDecimal`; response: texto decimal mediante `toPlainString()`, sin redondeo ni conversión a `double`.

Los campos de los DTO de request y response declaran explícitamente su nombre JSON mediante `@JsonProperty`.

## Paquetes

| Paquete | Contenido |
|---|---|
| `domain` | Modelos, `ApprovalRules`, `port/portin`, `port/portout`. |
| `application` | Casos de uso, DTO, mappers DTO-dominio, validadores manuales. |
| `infrastructure` | Router, handler, manejador HTTP de errores, adaptadores R2DBC, entidades de base, sus mappers, repositorios técnicos, transacciones y configuración. |
| `exception/application` | Datos inválidos, cliente inexistente, referencia en conflicto, solicitud no encontrada. |
| `exception/database` | `DuplicateReferenceException`, `PersistenceFailureException`; tipos propios sin dependencias de Spring o PostgreSQL. |

## Contratos

El handler inyecta `ProcessApplicationPort` y `QueryApplicationsPort`, implementados por los casos de uso. Nunca inyecta casos de uso concretos ni repositorios.

Los casos de uso inyectan `CustomerPort`, `ApplicationPort` y `TransactionPort`. Los adaptadores implementan esos puertos de salida. Cada adaptador R2DBC inyecta su repositorio, cuya interfaz extiende `R2dbcRepository`, no el driver.

Los puertos reciben modelos de dominio, nunca DTO de aplicación ni entidades R2DBC. El handler convierte DTO a `ApplicationData` mediante el mapper de aplicación. Infraestructura convierte entidades de base a dominio.

Dominio no importa aplicación ni infraestructura. Aplicación no importa infraestructura ni Spring. Excepciones propias dependen solo de Java. Reactor se permite en puertos y aplicación. Configurar casos de uso con `@Bean` en infraestructura; inyección por constructor. No crear ciclos entre casos de uso.

## Negocio y flujo

Entrada: `applicationReference`, `customerId`, `amount` (`BigDecimal`), `termMonths` (`Integer`). Aplicación valida presencia y textos no vacíos. JSON ilegible: 400.

Aprobar únicamente con monto positivo, plazo entre 6 y 60 inclusive, cliente existente y habilitado, y total aprobado sin superar el cupo. Guardar aprobaciones y rechazos, motivo y fecha. Los rechazos no consumen cupo.

Consultar primero la referencia. Si coincide cliente, valor numérico del monto y plazo, devolver el original sin reevaluar. Si cambian, 409 sin modificarlo. Esto también aplica al original rechazado.

Para una nueva referencia: abrir transacción, bloquear el cliente, consultar después el total aprobado en otra consulta, evaluar e insertar. Confirmar antes de responder. No sobrescribir registros.

## Transacciones y errores

`TransactionPort`: `<T> Mono<T> execute(Supplier<Mono<T>> operation)`. Su adaptador usa `TransactionalOperator` con `Mono.defer`, `R2dbcTransactionManager`, la misma `ConnectionFactory` de los repositorios y `READ_COMMITTED`. Aplicación no importa `TransactionalOperator`.

Bloqueo por cliente: `SELECT ... FOR UPDATE`. Referencia única en PostgreSQL. Si una inserción pierde por duplicidad, revertir; recuperar fuera de esa transacción consultando el original y comparándolo. Identificar específicamente la restricción de referencia.

Cliente inexistente: aplicación genera `CustomerNotFoundException`, la recupera dentro de la operación y guarda un rechazo `CUSTOMER_NOT_FOUND`; no responde 404. La persistencia debe permitir conservar ese identificador inexistente.

Usar `onErrorMap` en adaptadores para traducir fallos conocidos y `onErrorResume` para recuperaciones específicas. Una caída de base nunca significa rechazo de crédito. `GlobalErrorHandler` implementa `WebExceptionHandler`: mensaje público seguro y registro técnico único. No usar `.block()`, `.subscribe()` manual, recuperaciones genéricas que oculten errores ni operaciones paralelas dentro de la transacción.

## HTTP y local

Rutas: `POST /applications`, `GET /applications/{reference}`, `GET /applications?limit=20`. Nueva solicitud persistida: 201; repetición idéntica y consultas: 200; datos incompletos: 400; referencia consultada inexistente: 404; conflicto: 409; fallo técnico: 500.

Docker Compose: frontend, backend y PostgreSQL con volumen y comprobación de salud. Backend conecta a `postgres:5432`. Angular llama `/api/*`; el frontend reenvía al backend quitando ese prefijo. Frontend atómico: atoms, molecules, organisms, templates y pages; HTTP en servicios separados. Conservar referencia al reintentar.

Colas y arquitectura hexagonal del frontend quedan pendientes. Probar reglas, rechazos persistidos, concurrencia real, duplicados, reversión, respuestas HTTP y límites entre capas. No afirmar que esas pruebas ya se ejecutaron.
