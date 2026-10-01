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
| `domain` | Entidades de datos, fábricas en `factory`, reglas en `rule`, `port/portin` y `port/portout`. |
| `application` | Casos de uso, DTO, mappers DTO-dominio, validadores manuales. |
| `infrastructure` | Router, handler, manejador HTTP de errores, adaptadores R2DBC, entidades de base, sus mappers, repositorios técnicos, transacciones y configuración. |
| `exception/application` | Datos inválidos, cliente inexistente, clave de idempotencia en conflicto, solicitud no encontrada. |
| `exception/database` | `DuplicateIdempotencyKeyException`, `PersistenceFailureException`; tipos propios sin dependencias de Spring o PostgreSQL. |
| `exception/message` | Constantes de textos y códigos en `ValidationMessages`, `ApplicationMessages`, `DomainMessages`, `InfrastructureMessages` y `ErrorCodes`. |

Por decisión del usuario, centralizar los mensajes de validación, respuestas, excepciones, reglas y diagnósticos de infraestructura en `exception/message`. Usar clases `final` con constructor privado y constantes `public static final String`, referenciadas mediante el nombre de la clase. Las constantes no dependen de otras capas. Para mensajes con identificadores, usar plantillas `%s` y `.formatted(...)`. Conservar el texto existente: mensajes públicos en español y diagnósticos internos en inglés. Excluir los textos de Swagger de esta centralización.

## Contratos

El handler inyecta `ProcessApplicationPort` y `QueryApplicationsPort`, implementados por los casos de uso. Nunca inyecta casos de uso concretos ni repositorios.

Los casos de uso inyectan `CustomerPort`, `ApplicationPort` y `TransactionPort`. Los adaptadores implementan esos puertos de salida. Cada adaptador R2DBC inyecta su repositorio, cuya interfaz extiende `R2dbcRepository`, no el driver.

Los puertos reciben modelos de dominio, nunca DTO de aplicación ni entidades R2DBC. El handler convierte DTO a `ApplicationData` mediante el mapper de aplicación. Infraestructura convierte entidades de base a dominio.

Por decisión del usuario, los casos de uso trabajan internamente con `ApplicationDataDto`, `CustomerDto`, `CreditDecisionDto`, `CreditApplicationDto` y `ProcessingResultDto`. Las entidades de dominio se permiten únicamente en las firmas de los contratos y en los mappers. Convertir las entradas de puertos a DTO de inmediato y convertir a dominio al invocar puertos/reglas o devolver el resultado. No construir entidades de dominio en casos de uso ni almacenar entidades en campos de DTO. `ApplicationValidator.validate` recibe `ApplicationDataDto`. Los mappers estáticos `ApplicationDataDtoMapper`, `CustomerDtoMapper`, `CreditDecisionDtoMapper`, `CreditApplicationDtoMapper` y `ProcessingResultDtoMapper` implementan `toDto` / `toDomain` mediante builders, conservando los valores decimales, vínculo opcional, motivo histórico, fecha y señal de creación. `ApplicationDtoMapper` utiliza esos DTO internos para producir las respuestas HTTP.

Consultas implementadas en `application/usecase/QueryApplicationsUseCase`, que implementa `QueryApplicationsPort` y recibe `ApplicationPort` y `ApplicationValidator` por constructor. Valida referencia no nula ni blanca, página no negativa y tamaño de 1 a 100. Una referencia inexistente emite `ApplicationNotFoundException`; datos de consulta inválidos emiten `InvalidApplicationDataException`. Los fallos de persistencia se propagan. Validación y consulta se ejecutan al suscribirse mediante `Mono.defer`.

Dominio no importa aplicación ni infraestructura. Aplicación no importa infraestructura; los casos de uso se registran mediante `@Service`, por decisión posterior del proyecto. No importar APIs de persistencia o transacciones de Spring en aplicación. Excepciones propias dependen solo de Java. Reactor se permite en puertos y aplicación. Inyección por constructor con dependencias `final`. No crear ciclos entre casos de uso.

Por decisión del usuario, las entidades de dominio contienen únicamente campos y métodos generados por Lombok; no añadirles métodos de negocio, comparación ni fábricas estáticas. `ApplicationMatchingRules.matches(original, incoming)` realiza la comparación de reintentos. `CreditDecisionRules.isApproved` / `isRejected` comprueban el estado de una decisión. `CreditDecisionFactory.approved` / `rejected` y `ProcessingResultFactory.created` / `existing` construyen modelos mediante builders. Estas clases viven dentro del dominio y sus métodos son estáticos.

`ProcessApplicationUseCase` implementa el procesamiento completo y recibe `CustomerPort`, `ApplicationPort`, `TransactionPort`, `ApplicationValidator` y `ApprovalRules`. Valida la entrada, resuelve claves de idempotencia existentes y coordina bloqueo, lectura del total aprobado, evaluación e inserción dentro de la transacción. Recupera `DuplicateIdempotencyKeyException` fuera de la transacción fallida para devolver el original o emitir `IdempotencyConflictException`. `QueryApplicationsUseCase` recibe únicamente `ApplicationPort` como puerto de salida. `ApplicationValidator` se registra con `@Component`; `BusinessConfiguration` registra las reglas puras mediante `@Bean`, sin dependencias Spring en dominio.

Persistencia implementada en `infrastructure/r2dbc`: entidades `CustomerEntity` y `ApplicationEntity`, repositorios que extienden `R2dbcRepository`, mappers manuales estáticos con builders y adaptadores `CustomerR2dbcAdapter` / `ApplicationR2dbcAdapter`. El adaptador transaccional vive en `infrastructure/r2dbc/adapter/transaction`. La inserción usa `INSERT ... RETURNING`, nunca `save` ni actualización. La fecha definitiva la genera PostgreSQL. `CreditApplication.identifiedCustomerId` conserva el vínculo opcional; `CreditDecision.reasonDescription` conserva la explicación histórica del rechazo. Los mappers no consultan clientes ni reconstruyen el vínculo.

`R2dbcTransactionAdapter` implementa `TransactionPort` con `TransactionalOperator.execute` y `singleOrEmpty` para entregar el resultado después del commit. `PersistenceConfiguration` registra el gestor y el operador transaccional con `READ_COMMITTED` y la misma `ConnectionFactory`. `PersistenceErrorMapper` traduce fallos conocidos conservando la causa; solo la restricción `uq_credit_applications_idempotency_key` con SQLSTATE `23505` genera `DuplicateIdempotencyKeyException`. Una colisión inesperada de referencia es un fallo técnico. La base de ejecución se inicializa mediante los scripts del repositorio `compose-sistema-IAS`; el SQL en `src/test/resources` es exclusivamente para pruebas.

## Negocio y flujo

Entrada: `customerId`, `amount` (`BigDecimal`), `termMonths` (`Integer`) y cabecera obligatoria `Idempotency-Key` UUID completo. La clave se normaliza a minúsculas; la referencia no forma parte del request. JSON ilegible: 400.

Aprobar únicamente con monto positivo, plazo entre 6 y 60 inclusive, cliente existente y habilitado, y total aprobado sin superar el cupo. Guardar aprobaciones y rechazos, motivo y fecha. Los rechazos no consumen cupo.

`ApprovalRules.evaluate` implementa reglas puras y construye las decisiones mediante `CreditDecisionFactory`. Para un cliente conocido, el orden de rechazo es monto, plazo, habilitación y cupo. Completar exactamente el cupo está permitido. `ApplicationValidator.validate` comprueba únicamente presencia e identificadores no blancos; monto cero/negativo y plazo fuera del rango llegan a las reglas y producen rechazos persistidos. `ApplicationMatchingRules.matches` compara el monto mediante `compareTo` y conserva identificadores y datos sin normalización silenciosa. Las decisiones rechazadas conservan la descripción del motivo utilizada al evaluar.

Consultar primero la clave. Si coincide cliente, valor numérico del monto y plazo, devolver el original sin reevaluar, con la referencia original. Si cambian, 409 / `IDEMPOTENCY_CONFLICT` sin modificarlo. Esto también aplica al original rechazado.

Para una nueva clave: abrir transacción, bloquear el cliente, consultar después el total aprobado en otra consulta, evaluar e insertar. PostgreSQL genera referencia, ID y fecha. Confirmar antes de responder. No sobrescribir registros.

## Transacciones y errores

`TransactionPort`: `<T> Mono<T> execute(Supplier<Mono<T>> operation)`. Su adaptador usa `TransactionalOperator` con `Mono.defer`, `R2dbcTransactionManager`, la misma `ConnectionFactory` de los repositorios y `READ_COMMITTED`. Aplicación no importa `TransactionalOperator`.

Bloqueo por cliente: `SELECT ... FOR UPDATE`. Referencia y clave únicas en PostgreSQL. Si una inserción pierde por duplicidad de clave, revertir; recuperar fuera de esa transacción consultando el original por clave y comparándolo. Identificar específicamente la restricción de idempotencia.

Cliente inexistente: aplicación genera `CustomerNotFoundException`, la recupera dentro de la operación y guarda un rechazo `CUSTOMER_NOT_FOUND`; no responde 404. La persistencia debe permitir conservar ese identificador inexistente.

Usar `onErrorMap` en adaptadores para traducir fallos conocidos y `onErrorResume` para recuperaciones específicas. Una caída de base nunca significa rechazo de crédito. `GlobalErrorHandler` implementa `WebExceptionHandler`: mensaje público seguro y registro técnico único. No usar `.block()`, `.subscribe()` manual, recuperaciones genéricas que oculten errores ni operaciones paralelas dentro de la transacción.

## HTTP y local

`README.md` contiene el análisis de arquitectura, diagramas Mermaid, estructura relevante de carpetas, puesta en marcha, endpoints y respuestas para el frontend, reglas de referencias y comandos de pruebas. Mantenerlo actualizado cuando cambien los endpoints, sus DTO o el despliegue.

Swagger implementado con `springdoc-openapi-starter-webflux-ui:3.1.1` para Spring Boot 4. `ApplicationRouter` documenta las tres rutas funcionales mediante `@RouterOperations` / `@RouterOperation`, con esquemas de DTO, estados HTTP y parámetros. `OpenApiConfiguration` contiene los metadatos de la API; los DTO HTTP usan `@Schema` para ejemplos y descripciones en español, sin validación automática. Acceder a `/swagger-ui.html`, `/v3/api-docs` o `/v3/api-docs.yaml`. Se verificaron Swagger UI, su configuración y la generación del documento por HTTP en una instancia temporal; no se enviaron solicitudes de crédito ni se ejecutaron pruebas automatizadas.

Rutas de solicitudes: `POST /applications`, `GET /applications/{reference}`, `GET /applications?page=0&size=20`. Nueva solicitud persistida: 201; repetición idéntica y consultas: 200; datos incompletos o paginación inválida: 400; referencia consultada inexistente: 404; conflicto: 409; fallo técnico: 500.

`ApplicationResponseDto.message` indica «Esta solicitud fue aprobada/rechazada» para nuevas solicitudes y consultas. En POST, el mapper recibe `ProcessingResult` y usa «Esta solicitud ya fue aprobada/rechazada» cuando `created` es falso. El mensaje es de presentación y no se guarda en la base. Los reintentos conservan los datos, el motivo y la fecha originales.

Entrada HTTP implementada en `infrastructure/routerhandler`: `router/ApplicationRouter` registra las tres rutas mediante `RouterFunction`; `handler/ApplicationHandler` inyecta únicamente `ProcessApplicationPort` y `QueryApplicationsPort` y usa mappers estáticos. POST interpreta el request, convierte a dominio y selecciona 201 o 200 según `ProcessingResult.created`. GET por referencia devuelve el response DTO; el listado usa página cero y tamaño 20 por defecto, con respuesta paginada. Un cuerpo vacío, JSON ilegible o parámetro de paginación no interpretable como entero genera `ServerWebInputException`. La validación de página no negativa y tamaño 1–100 corresponde a aplicación. `limit` se rechaza explícitamente con 400.

`error/GlobalErrorHandler` implementa `WebExceptionHandler` con prioridad anterior al manejador predeterminado de Spring Boot. Devuelve `ErrorResponseDto` construido mediante builder, con `code`, `message` y `traceId`, además de la cabecera `X-Trace-Id`. Traduce datos inválidos a 400, solicitud inexistente a 404, clave en conflicto a 409 y fallos técnicos a 500 con mensaje público genérico. Mantiene los estados HTTP de errores de transporte conocidos. Los mensajes públicos están en español; los fallos 5xx se registran una vez con la excepción original y la correlación. Utiliza el `ObjectMapper` de Jackson 3 administrado por Spring Boot.

Docker del backend incluido en la raíz: `Dockerfile` multietapa con Java 21 y `.dockerignore`. El usuario eliminó el `compose.yaml` del backend y dejó su integración Docker para una etapa posterior; no restaurarlo. El healthcheck del Dockerfile consulta `/actuator/health`. La imagen inicial se construyó y se verificó en un contenedor temporal antes de añadir Actuator; los scripts del proyecto hermano `compose-sistema-IAS` no se modificaron. `README.md` documenta la construcción de imagen y la puesta en marcha local, sin valores de conexión personales.

Spring Boot Actuator añadido mediante `spring-boot-starter-actuator`. `GET /actuator/health` responde HTTP 200 con `status: UP` y HTTP 503 con `status: DOWN` cuando falla la conexión R2DBC. La respuesta incluye los grupos `liveness` y `readiness` de Spring Boot 4, pero no componentes ni detalles. Management expone únicamente `health`. La operación se incluye en Swagger y `/v3/api-docs` mediante `springdoc.show-actuator=true`. Las pruebas HTTP verifican salud disponible, conexión fallida y presencia en OpenAPI; la falla se inyecta en el `ConnectionFactory` mediante un spy, reiniciado tras cada escenario.

Integración prevista con Angular: llamadas `/api/*` mediante un proxy que quita ese prefijo; conservar la clave y los datos al reintentar. Recibir la referencia para consultas y tickets. Frontend atómico: atoms, molecules, organisms, templates y pages; HTTP en servicios separados.

## Verificación unitaria

El usuario reanudó la ejecución de pruebas unitarias y pidió incluir los mappers, adaptadores y excepciones. Suite unitaria ampliada: 127 pruebas aprobadas, sin fallos ni omitidas; 52 corresponden a mappers de aplicación y R2DBC, incluidas conversiones DTO/dominio, precisión decimal, datos opcionales, motivo histórico, fechas con zona horaria, mensajes de reintento y traducción de errores de persistencia/timeouts. `DomainDtoMappersTest` cubre conjuntamente los cinco mappers de DTO internos; `ApplicationDtoMapperTest` cubre la entrada y respuesta HTTP. El resumen, la paginación y las referencias automáticas se comprueban mediante flujos HTTP y persistencia real.

Las 39 pruebas unitarias de adaptadores cubren `ApplicationR2dbcAdapter` (23), `CustomerR2dbcAdapter` (6) y `R2dbcTransactionAdapter` (10). Usan repositorios simulados; en transacciones usan el `TransactionalOperator` real con un `ReactiveTransactionManager` simulado. Verifican ejecución diferida, vacíos, orden del repositorio, límite/offset recibido, resultado persistido, traducción de errores, consulta por clave, commit antes de emitir, rollback antes del error/timeout, fallo de commit, creación diferida de la operación y cancelación. No requieren base de datos ni contexto Spring.

Las 16 pruebas de excepciones cubren las cuatro de aplicación mediante `ApplicationExceptionsTest` (12) y las dos de persistencia mediante `DatabaseExceptionsTest` (4). Verifican mensajes, identificadores sin normalización ni interpretación de caracteres de formato, detalle de validación y conservación de la causa original y su cadena. Las constantes se comprueban a través de los mensajes que producen sus consumidores. Ejecutar `gradlew.bat test` con filtros `com.backend_IAS.demo.domain.*`, `com.backend_IAS.demo.application.*`, `com.backend_IAS.demo.infrastructure.r2dbc.mapper.*`, `com.backend_IAS.demo.infrastructure.r2dbc.adapter.*` y `com.backend_IAS.demo.exception.*`.

## Resumen de cupo y resiliencia

`RESUMEN_CUPO.md` documenta `GET /customers/{customerId}/credit-summary`, con `customerId`, `status`, `creditLimit`, `approvedAmount` y `availableAmount`. Montos como texto decimal sin escala fija. Se consulta con un solo snapshot SQL, suma únicamente aprobaciones y no bloquea al cliente. El disponible es como mínimo cero. `CustomerCreditSummary` es un modelo de datos; los puertos `QueryCustomerCreditPort` y `CustomerCreditSummaryPort`, DTO internos y mappers manuales conservan los límites del proyecto. HTTP usa `CustomerHandler` / `CustomerRouter`; un cliente inexistente en GET produce 404 sin cambiar el rechazo persistido del POST. La integración con Angular se deja como contrato por elección del usuario; no se modificó `front-IAS`.

`RESILIENCIA.md` documenta los límites de conexión, creación y adquisición (3 s), bloqueo (2 s), sentencias (5 s), validación de pool (2 s) y ejecución de operación dentro de transacción (10 s). El timeout de operación se coloca dentro del callback de `TransactionalOperator`, para entregar el error después del rollback. No abarca commit ni rollback y no promete un deadline global HTTP. `PersistenceErrorMapper` reconoce timeouts y conserva su causa; HTTP responde 503 / `DATABASE_TIMEOUT`.

Rate limiting implementado con Bucket4j y Caffeine, configurable en `app.rate-limit.*`: 30 permisos iniciales y reposición continua de 30/minuto por IP, máximo 10.000 clientes. Filtro reactivo para POST `/applications` con la misma coincidencia de ruta que el router; errores 429 / `RATE_LIMIT_EXCEEDED`, `Retry-After` y correlación. Usa la IP de la conexión remota; `server.forward-headers-strategy=none`. GET y salud no consumen permisos. El estado es local, se reinicia tras expulsión/reinicio y puede compartirse detrás de proxy. El usuario descartó circuit breaker y pospuso RabbitMQ.

## Verificación automatizada ampliada

El usuario autorizó modificar `compose-sistema-IAS/db/init` para reconstruir la base: secuencia de referencias desde `REF-001`, función de padding mínimo de tres dígitos sin truncar, valor predeterminado de referencia y clave de idempotencia UUID textual obligatoria/única. Se amplió el permiso del usuario de aplicación sobre la nueva secuencia. No se borró ni reconstruyó la base del usuario. Los scripts reales se ejecutaron en un contenedor PostgreSQL 17 temporal; el usuario restringido insertó una aprobación y un rechazo y obtuvo `REF-001`/`REF-002`. Se verifican concurrencia, reintentos, conflictos, cabecera inválida/múltiple/ausente, normalización y transición `REF-999` → `REF-1000`. El contrato de consumo está en `front-IAS/REFERENCIAS_AUTOMATICAS.md`. Las referencias pueden tener huecos. No generar ni recibir la referencia de creación desde el frontend.

Paginación del historial implementada reemplazando el contrato anterior de recientes: `ApplicationPage`, `ApplicationPageFactory`, DTO interno/HTTP y mappers manuales; puertos `findPage(int page, int size)`. SQL usa `LIMIT` / `OFFSET` y `COUNT(*)` en una sola sentencia para mantener contenido y total coherentes. Offset calculado como `long`. Defaults 0/20, tamaño 1–100, página no negativa; vacíos y páginas posteriores responden 200 con sus totales. Se mantiene el orden fecha/id descendente y la consulta por referencia independiente. La cuota de POST no limita este GET. Documento de uso creado en `front-IAS/PAGINACION_SOLICITUDES.md`, sin código de implementación Angular; su guía previa de resumen/resiliencia enlaza al nuevo contrato.

El usuario autorizó las pruebas automatizadas de los flujos principales. `integration/ApplicationFlowsIntegrationTest` tiene 85 escenarios con servidor HTTP en puerto aleatorio y PostgreSQL 17 aislado mediante Testcontainers. El backend usa el usuario restringido `flow_app`, creado por `src/test/resources/flow-test-user.sql`; una conexión administrativa separada reinicializa `persistence-test-schema.sql` y aplica permisos equivalentes a los de Compose antes de cada escenario. El esquema de prueba se ajustó para que un vínculo nulo no implique obligatoriamente `CUSTOMER_NOT_FOUND`, conservando la implicación inversa exigida por el SQL real. Ahora reproduce también la secuencia y clave de idempotencia de los scripts actualizados de Compose.

Se verificaron aprobaciones y consultas, precisión decimal, consumo acumulado, cupo exacto, rechazos persistidos y precedencia, reintentos numéricamente idénticos sin consumo extra, conservación del rechazo aunque cambie el cliente, conflictos sin sobrescritura, campos faltantes y blancos, JSON y tipos inválidos, cuerpo vacío, contenido no soportado, páginas y tamaños, errores con `traceId` / `X-Trace-Id`, fallos de persistencia, rollback por error de inserción y fallo de commit diferido sin respuesta exitosa. Los casos concurrentes verifican cupo por cliente, misma clave con datos idénticos (cliente conocido o inexistente) y conflictos de clave entre clientes distintos. Un spy sincroniza las búsquedas iniciales vacías por clave para forzar la carrera de duplicados; las operaciones de base y transacciones son reales.

Los escenarios nuevos comprueban resúmenes sin consumo, aprobación parcial/exacta, precisión, reintentos, reducción de cupo, clientes bloqueados/inexistentes y consulta sin bloqueo. Los timeouts se prueban con bloqueo real, trigger lento y agotamiento de un pool aislado de cuatro conexiones; se verifica ausencia de consumo y recuperación. `RateLimitingIntegrationTest` añade un escenario HTTP real con cuota reducida para comprobar 429 sin persistencia y disponibilidad de GET/salud. Los flujos generales desactivan la cuota; la prueba específica la habilita. Las pruebas unitarias adicionales comprueban rollback antes de timeout, traducción de causas, reposición por reloj controlado y permisos concurrentes.

Última suite completa: `gradlew.bat test` pasó 229 pruebas, sin fallos ni omitidas: 127 unitarias, 86 flujos HTTP, 15 integraciones R2DBC y una carga de contexto Spring. Docker debe estar disponible. El reporte está en `build/reports/tests/test/index.html`. Colas y arquitectura hexagonal del frontend quedan pendientes.
