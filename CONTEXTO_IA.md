# Contexto de implementación: core de créditos

Especificación resumida. `ARQUITECTURA.md` contiene las decisiones completas y sus fuentes. Estado: implementación parcial; revisar el avance descrito antes de asumir que una funcionalidad, tabla o contenedor ya está implementado. Añadir funcionalidades únicamente conforme a las solicitudes del usuario.

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

Consultas implementadas en `application/usecase/QueryApplicationsUseCase`, que implementa `QueryApplicationsPort` y recibe `ApplicationPort` y `ApplicationValidator` por constructor. Valida referencia no nula ni blanca y límite de 1 a 100. Una referencia inexistente emite `ApplicationNotFoundException`; datos de consulta inválidos emiten `InvalidApplicationDataException`. Los fallos de persistencia se propagan. Validación y consulta se ejecutan al suscribirse mediante `Mono.defer` / `Flux.defer`.

Dominio no importa aplicación ni infraestructura. Aplicación no importa infraestructura; los casos de uso se registran mediante `@Service`, por decisión posterior del proyecto. No importar APIs de persistencia o transacciones de Spring en aplicación. Excepciones propias dependen solo de Java. Reactor se permite en puertos y aplicación. Inyección por constructor con dependencias `final`. No crear ciclos entre casos de uso.

`ProcessApplicationUseCase` implementa el procesamiento completo y recibe `CustomerPort`, `ApplicationPort`, `TransactionPort`, `ApplicationValidator` y `ApprovalRules`. Valida la entrada, resuelve referencias existentes y coordina bloqueo, lectura del total aprobado, evaluación e inserción dentro de la transacción. Recupera `DuplicateReferenceException` fuera de la transacción fallida para devolver el original o emitir `ReferenceConflictException`. `QueryApplicationsUseCase` recibe únicamente `ApplicationPort` como puerto de salida. `ApplicationValidator` se registra con `@Component`; `BusinessConfiguration` registra las reglas puras mediante `@Bean`, sin dependencias Spring en dominio.

Persistencia implementada en `infrastructure/r2dbc`: entidades `CustomerEntity` y `ApplicationEntity`, repositorios que extienden `R2dbcRepository`, mappers manuales estáticos con builders y adaptadores `CustomerR2dbcAdapter` / `ApplicationR2dbcAdapter`. El adaptador transaccional vive en `infrastructure/r2dbc/adapter/transaction`. La inserción usa `INSERT ... RETURNING`, nunca `save` ni actualización. La fecha definitiva la genera PostgreSQL. `CreditApplication.identifiedCustomerId` conserva el vínculo opcional; `CreditDecision.reasonDescription` conserva la explicación histórica del rechazo. Los mappers no consultan clientes ni reconstruyen el vínculo.

`R2dbcTransactionAdapter` implementa `TransactionPort` con `TransactionalOperator.execute` y `singleOrEmpty` para entregar el resultado después del commit. `PersistenceConfiguration` registra el gestor y el operador transaccional con `READ_COMMITTED` y la misma `ConnectionFactory`. `PersistenceErrorMapper` traduce fallos conocidos conservando la causa; solo la restricción `uq_credit_applications_reference` con SQLSTATE `23505` genera `DuplicateReferenceException`. La base de ejecución se inicializa mediante los scripts del repositorio `compose-sistema-IAS`; el SQL en `src/test/resources` es exclusivamente para pruebas.

Por solicitud del usuario, la conexión R2DBC de desarrollo está configurada temporalmente con URL y credenciales directas en `application.properties`, usando el usuario de aplicación y la base `DATABAE_IAS` expuesta por Docker en `localhost:5432`. Ejecutar con el perfil predeterminado; no se importa el `.env` de Compose. Más adelante se sustituirán las credenciales directas por variables de entorno. `spring.sql.init.mode=never`: el backend no ejecuta scripts de inicialización. Si el backend se ejecuta dentro de la red de Compose, la URL debe usar el host `postgres` y el puerto interno `5432`.

## Negocio y flujo

Entrada: `applicationReference`, `customerId`, `amount` (`BigDecimal`), `termMonths` (`Integer`). Aplicación valida presencia y textos no vacíos. JSON ilegible: 400.

Aprobar únicamente con monto positivo, plazo entre 6 y 60 inclusive, cliente existente y habilitado, y total aprobado sin superar el cupo. Guardar aprobaciones y rechazos, motivo y fecha. Los rechazos no consumen cupo.

`ApprovalRules.evaluate` implementa reglas puras. Para un cliente conocido, el orden de rechazo es monto, plazo, habilitación y cupo. Completar exactamente el cupo está permitido. `ApplicationValidator.validate` comprueba únicamente presencia e identificadores no blancos; monto cero/negativo y plazo fuera del rango llegan a las reglas y producen rechazos persistidos. `ApplicationData.matches` compara el monto mediante `compareTo` y conserva identificadores y datos sin normalización silenciosa. Las decisiones rechazadas conservan la descripción del motivo utilizada al evaluar.

Consultar primero la referencia. Si coincide cliente, valor numérico del monto y plazo, devolver el original sin reevaluar. Si cambian, 409 sin modificarlo. Esto también aplica al original rechazado.

Para una nueva referencia: abrir transacción, bloquear el cliente, consultar después el total aprobado en otra consulta, evaluar e insertar. Confirmar antes de responder. No sobrescribir registros.

## Transacciones y errores

`TransactionPort`: `<T> Mono<T> execute(Supplier<Mono<T>> operation)`. Su adaptador usa `TransactionalOperator` con `Mono.defer`, `R2dbcTransactionManager`, la misma `ConnectionFactory` de los repositorios y `READ_COMMITTED`. Aplicación no importa `TransactionalOperator`.

Bloqueo por cliente: `SELECT ... FOR UPDATE`. Referencia única en PostgreSQL. Si una inserción pierde por duplicidad, revertir; recuperar fuera de esa transacción consultando el original y comparándolo. Identificar específicamente la restricción de referencia.

Cliente inexistente: aplicación genera `CustomerNotFoundException`, la recupera dentro de la operación y guarda un rechazo `CUSTOMER_NOT_FOUND`; no responde 404. La persistencia debe permitir conservar ese identificador inexistente.

Usar `onErrorMap` en adaptadores para traducir fallos conocidos y `onErrorResume` para recuperaciones específicas. Una caída de base nunca significa rechazo de crédito. `GlobalErrorHandler` implementa `WebExceptionHandler`: mensaje público seguro y registro técnico único. No usar `.block()`, `.subscribe()` manual, recuperaciones genéricas que oculten errores ni operaciones paralelas dentro de la transacción.

## HTTP y local

Rutas: `POST /applications`, `GET /applications/{reference}`, `GET /applications?limit=20`. Nueva solicitud persistida: 201; repetición idéntica y consultas: 200; datos incompletos: 400; referencia consultada inexistente: 404; conflicto: 409; fallo técnico: 500.

Entrada HTTP implementada en `infrastructure/routerhandler`: `router/ApplicationRouter` registra las tres rutas mediante `RouterFunction`; `handler/ApplicationHandler` inyecta únicamente `ProcessApplicationPort` y `QueryApplicationsPort` y usa `ApplicationDtoMapper` estático. POST interpreta el request, convierte a dominio y selecciona 201 o 200 según `ProcessingResult.created`. GET por referencia devuelve el response DTO; el listado usa límite 20 por defecto y reúne los resultados antes de construir la respuesta JSON. Un cuerpo vacío, JSON ilegible o límite no interpretable como entero genera `ServerWebInputException`. La validación del rango 1–100 corresponde a aplicación.

`error/GlobalErrorHandler` implementa `WebExceptionHandler` con prioridad anterior al manejador predeterminado de Spring Boot. Devuelve `ErrorResponseDto` construido mediante builder, con `code`, `message` y `traceId`, además de la cabecera `X-Trace-Id`. Traduce datos inválidos a 400, solicitud inexistente a 404, referencia en conflicto a 409 y fallos técnicos a 500 con mensaje público genérico. Mantiene los estados HTTP de errores de transporte conocidos. Los mensajes públicos están en español; los fallos 5xx se registran una vez con la excepción original y la correlación. Utiliza el `ObjectMapper` de Jackson 3 administrado por Spring Boot.

Docker Compose: frontend, backend y PostgreSQL con volumen y comprobación de salud. Backend conecta a `postgres:5432`. Angular llama `/api/*`; el frontend reenvía al backend quitando ese prefijo. Frontend atómico: atoms, molecules, organisms, templates y pages; HTTP en servicios separados. Conservar referencia al reintentar.

Colas y arquitectura hexagonal del frontend quedan pendientes. Probar reglas, rechazos persistidos, concurrencia real, duplicados, reversión, respuestas HTTP y límites entre capas. No afirmar que esas pruebas ya se ejecutaron.
