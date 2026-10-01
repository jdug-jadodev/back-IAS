# Arquitectura del core de créditos

**Estado:** flujo backend implementado para procesar y consultar solicitudes: modelos, puertos, DTO, mappers, validaciones, reglas, casos de uso, adaptadores R2DBC, router, handler y manejo global de errores. La base local se inicializa mediante los scripts de Compose. Suite automatizada completa aprobada: 174 pruebas, incluidas 47 de flujos HTTP con PostgreSQL aislado, concurrencia, reintentos, rechazos y fallos transaccionales.

**Base:** el enunciado exige procesar, conservar y consultar solicitudes, proteger el cupo ante concurrencia y conservar el resultado original ante referencias repetidas. También exige Java con WebFlux y Angular. [^prueba]

La arquitectura interna, PostgreSQL, R2DBC, Docker, Lombok y las validaciones manuales son decisiones del proyecto. Para completar los puntos abiertos, se propone ubicar las reglas puras en dominio y aislar las transacciones mediante un puerto.

## 1. Decisiones principales

| Decisión | Motivo |
|---|---|
| Un backend con arquitectura hexagonal | Separar el negocio de HTTP, la base de datos y las futuras colas. |
| WebFlux con router y handler | Separar la selección de la ruta del manejo de la petición. [^webflux] |
| Spring Data R2DBC y PostgreSQL | Mantener el acceso reactivo y proteger los datos en la base de datos. |
| `TransactionalOperator` detrás de una interfaz | Agrupar el procesamiento sin importar clases de Spring en aplicación. [^transacciones] |
| Clases Java y Lombok; sin `record` | Mantener clases explícitas y reducir código repetitivo de constructores y accesores. |
| Validaciones manuales; sin `@Valid` ni restricciones automáticas en DTO | Hacer visible dónde se valida y qué ocurre cuando una validación falla. |
| Docker Compose para ejecución local | Definir frontend, backend y PostgreSQL como servicios separados; cola opcional. |
| Frontend Angular con diseño atómico | Organizar componentes visuales reutilizables; la arquitectura hexagonal del frontend queda pendiente. |

Convención: código, comentarios y pruebas en inglés; mensajes destinados al cliente en español.

## 2. Estructura del backend

Carpetas dentro del paquete Java principal. Los nombres de clases son los propuestos para la implementación.

```text
com/backend_IAS/demo/
├── DemoApplication.java
├── domain/
│   ├── entity/
│   ├── enums/
│   ├── factory/
│   │   ├── CreditDecisionFactory.java
│   │   └── ProcessingResultFactory.java
│   ├── rule/
│   │   ├── ApprovalRules.java
│   │   ├── ApplicationMatchingRules.java
│   │   └── CreditDecisionRules.java
│   └── port/
│       ├── portin/
│       │   ├── ProcessApplicationPort.java
│       │   └── QueryApplicationsPort.java
│       └── portout/
│           ├── CustomerPort.java
│           ├── ApplicationPort.java
│           └── TransactionPort.java
├── application/
│   ├── usecase/
│   │   ├── ProcessApplicationUseCase.java
│   │   └── QueryApplicationsUseCase.java
│   ├── dto/
│   ├── mapper/
│   └── validation/
├── infrastructure/
│   ├── input/http/
│   │   ├── ApplicationRouter.java
│   │   ├── ApplicationHandler.java
│   │   └── GlobalErrorHandler.java
│   ├── output/r2dbc/
│   │   ├── adapter/
│   │   ├── entity/
│   │   ├── mapper/
│   │   └── repository/
│   ├── transaction/
│   │   └── R2dbcTransactionAdapter.java
│   └── configuration/
└── exception/
    ├── application/
    ├── database/
    └── message/
```

No crear adaptadores vacíos para colas. Se incorporarán cuando exista esa integración.

## 3. Qué pertenece a cada capa

### Dominio: datos y reglas del crédito

Contiene modelos, fábricas, reglas de aprobación y puertos de entrada y salida.

| Modelo conceptual | Contenido |
|---|---|
| `ApplicationData` | `applicationReference`, `customerId`, `amount`, `termMonths`. Representa lo recibido, todavía sin aprobar. |
| `Customer` | Identificador, estado y cupo máximo. |
| `CreditDecision` | Aprobación o rechazo, con motivo y descripción histórica cuando corresponda. |
| `CreditApplication` | Datos de la solicitud, decisión, fecha de procesamiento y `identifiedCustomerId` opcional. |
| `ProcessingResult` | Solicitud procesada y un indicador `created`, para distinguir creación de repetición. |

Usar `BigDecimal` para montos, `Integer` para el plazo recibido e `Instant` para la fecha. Son modelos del negocio, **no entidades de base de datos**.

`ApprovalRules` evalúa monto, plazo, habilitación y cupo con los datos que recibe, en ese orden para clientes conocidos. No consulta repositorios ni depende de Spring. `BusinessConfiguration` la registra como bean. `ApplicationData` permite representar valores que luego producirán un rechazo; su constructor no los descarta antes de evaluarlos. `ApplicationMatchingRules.matches(original, incoming)` realiza la comparación numérica del monto para reintentos.

Las entidades de dominio son contenedores de datos con Lombok: no contienen métodos de negocio, comparación ni fábricas estáticas. La construcción de decisiones y resultados vive en `domain/factory/CreditDecisionFactory` y `ProcessingResultFactory`, con métodos estáticos y builders. `domain/rule/CreditDecisionRules` contiene las comprobaciones `isApproved` / `isRejected`. La lógica extraída conserva su comportamiento y se utiliza desde las reglas y los casos de uso.

**No habrá dos grupos de casos de uso.** Las reglas puras quedan en dominio; la coordinación del proceso queda en aplicación.

### Aplicación: coordinar el trabajo

Los casos de uso implementan los puertos de entrada. Validan manualmente, consultan puertos de salida, ejecutan las reglas y solicitan el guardado.

Aquí viven los DTO HTTP (`ApplicationRequestDto`, `ApplicationResponseDto`, `ErrorResponseDto`), los DTO internos (`ApplicationDataDto`, `CustomerDto`, `CreditDecisionDto`, `CreditApplicationDto`, `ProcessingResultDto`), sus mappers y `ApplicationValidator`. Los DTO son contenedores de datos, sin validaciones automáticas ni lógica de aprobación. `ApplicationValidator.validate` recibe `ApplicationDataDto`.

Por decisión del usuario, la coordinación interna de aplicación trabaja únicamente con DTO. Las entidades de dominio aparecen en las firmas exigidas por los puertos y en los mappers. Al entrar a un caso de uso o recibir datos de un puerto, convertir a DTO antes de trabajar con ellos; al invocar un puerto o una regla de dominio, convertir a dominio; al devolver el resultado del caso de uso, convertir nuevamente a dominio. No construir entidades directamente en los casos de uso ni mantenerlas en DTO como campos anidados.

Estado actual de consultas: `QueryApplicationsUseCase` implementa `QueryApplicationsPort`, valida mediante `ApplicationValidator` y consulta `ApplicationPort`. La referencia debe estar presente y no estar en blanco; el límite debe estar entre 1 y 100. Si la búsqueda queda vacía, emite `ApplicationNotFoundException`. Los datos inválidos generan `InvalidApplicationDataException` y los fallos técnicos se propagan. La validación y el acceso al puerto se difieren hasta la suscripción.

Estado actual de procesamiento: `ProcessApplicationUseCase` implementa `ProcessApplicationPort`, valida los cuatro campos y resuelve el reintento antes de abrir la transacción. Para solicitudes nuevas bloquea al cliente, consulta el total aprobado en otra operación, evalúa e inserta. Captura la ausencia de cliente dentro de la transacción para guardar `CUSTOMER_NOT_FOUND`. Recupera la referencia duplicada fuera de la transacción revertida. El resultado llega al handler después de confirmar.

Los mappers son totalmente manuales: métodos estáticos y construcción mediante builders. `ApplicationDtoMapper.toDomain(ApplicationRequestDto)` convierte a `ApplicationData`; `toResponse(CreditApplication)` construye `ApplicationResponseDto` pasando primero por el DTO interno. `ApplicationDataDtoMapper`, `CustomerDtoMapper`, `CreditDecisionDtoMapper`, `CreditApplicationDtoMapper` y `ProcessingResultDtoMapper` proporcionan conversiones `toDto` / `toDomain`. Conservan explícitamente todos los datos, incluida la escala de `BigDecimal`, `identifiedCustomerId`, `reasonDescription`, `processedAt` y `created`. El monto del request y de los DTO internos es `BigDecimal`; en el response HTTP se devuelve como `String` mediante `toPlainString()`, sin redondearlo.

**Los puertos del dominio no reciben DTO de aplicación.** El handler usa `ApplicationDtoMapper` para convertir el DTO a `ApplicationData`; luego llama al puerto. Así dominio no necesita importar aplicación.

### Infraestructura: conectar con tecnologías

Contiene HTTP, adaptadores R2DBC, entidades de base de datos, sus mappers, configuración de dependencias y transacciones.

Swagger UI se integra con `springdoc-openapi-starter-webflux-ui:3.1.1`, compatible con Spring Boot 4. Las tres rutas funcionales se documentan en `ApplicationRouter` mediante `@RouterOperations` / `@RouterOperation`, asociadas a sus métodos del handler. Se describen request, respuestas, reintentos, rechazos persistidos y parámetros de consulta. Los DTO HTTP añaden `@Schema` únicamente como documentación, sin validación automática. `OpenApiConfiguration` define título, versión y etiqueta. Accesos locales: `/swagger-ui.html`, `/v3/api-docs` y `/v3/api-docs.yaml`. Swagger UI y el documento generado se verificaron por HTTP en una instancia temporal, sin enviar solicitudes de crédito ni ejecutar pruebas automatizadas.

Cada adaptador R2DBC implementa un puerto de salida e inyecta su repositorio técnico. Por ejemplo, `CustomerR2dbcAdapter` implementa `CustomerPort` y usa `CustomerR2dbcRepository`.

La interfaz técnica extiende **`R2dbcRepository` de Spring Data**, no el driver PostgreSQL. El driver se configura como parte de la conexión. [^repositorios]

Los mappers de infraestructura convierten `CustomerEntity` y `ApplicationEntity` a modelos de dominio y viceversa. No validan reglas ni deciden aprobaciones.

Adaptadores implementados en `infrastructure/r2dbc`: `CustomerR2dbcAdapter` obtiene al cliente mediante `SELECT ... FOR UPDATE`; `ApplicationR2dbcAdapter` consulta referencias, suma únicamente aprobaciones, inserta mediante `INSERT ... RETURNING` y lista por `processed_at DESC, id DESC`. Los repositorios extienden `R2dbcRepository`. La inserción no utiliza `save` ni sobrescribe resultados anteriores. El adaptador de transacciones vive en `infrastructure/r2dbc/adapter/transaction`.

`ApplicationData.customerId` conserva el identificador recibido; `CreditApplication.identifiedCustomerId` conserva el vínculo opcional con el cliente conocido. `CreditDecision.reasonDescription` conserva el mensaje histórico. El mapper mapea esos datos explícitamente y la respuesta utiliza el mensaje persistido cuando existe. La fecha definitiva se obtiene de PostgreSQL mediante `RETURNING` y se convierte de `OffsetDateTime` a `Instant`.

`PersistenceErrorMapper` traduce únicamente fallos técnicos conocidos y conserva su causa. La duplicidad de referencia se identifica mediante SQLSTATE `23505` y el nombre exacto `uq_credit_applications_reference`, utilizando los detalles del driver PostgreSQL; otras restricciones generan `PersistenceFailureException`.

### Excepciones: tipos compartidos, separados por origen

Este paquete reúne excepciones propias de aplicación y persistencia. Sus clases dependen solo de Java: no importan HTTP, Spring ni el driver. Pueden conservar la causa original como `Throwable`.

`exception/message` centraliza los textos del código en clases `final` con constructor privado y constantes `public static final String`: `ValidationMessages`, `ApplicationMessages`, `DomainMessages`, `InfrastructureMessages` y `ErrorCodes`. Los consumidores referencian las constantes; los textos que incluyen identificadores usan plantillas `%s` y `.formatted(...)`. Los mensajes públicos permanecen en español y los diagnósticos internos en inglés. Estas clases no dependen de otras capas. Los textos de Swagger se mantienen en sus anotaciones y configuración, por indicación del usuario.

El adaptador interpreta el error técnico; aplicación decide las recuperaciones previstas; `GlobalErrorHandler` construye la respuesta HTTP. Las excepciones no escriben logs por sí mismas.

## 4. Contratos e inyección

**Dependemos de contratos, no de implementaciones concretas de casos de uso o adaptadores.**

```text
Router → Handler → ProcessApplicationPort
                         ↑ implementa
                ProcessApplicationUseCase
                         ↓ usa
             CustomerPort / ApplicationPort
                         ↑ implementan
                Adaptadores R2DBC
                         ↓ usan
                Repositorios R2DBC
                         ↓
                     PostgreSQL
```

| Contrato | Operaciones previstas | Implementación |
|---|---|---|
| `ProcessApplicationPort` | `process(ApplicationData)` → `Mono<ProcessingResult>` | `ProcessApplicationUseCase` |
| `QueryApplicationsPort` | `findByReference(String)` → `Mono<CreditApplication>`; `findRecent(int)` → `Flux<CreditApplication>` | `QueryApplicationsUseCase` |
| `CustomerPort` | `findWithLock(String)` → `Mono<Customer>`; vacío si no existe | `CustomerR2dbcAdapter` |
| `ApplicationPort` | `findByReference(String)`, `getTotalApproved(String)`, `insert(CreditApplication)` y `listRecent(int)` | `ApplicationR2dbcAdapter` |
| `TransactionPort` | Ejecutar una operación como una sola transacción | `R2dbcTransactionAdapter` |

`ApplicationPort` recibe y devuelve modelos de dominio o valores simples, nunca entidades R2DBC. `insert` significa **crear sin sobrescribir**: no actualizar una solicitud existente.

El handler inyecta los puertos de entrada y utiliza los métodos estáticos de `ApplicationDtoMapper`. No inyecta puertos de persistencia ni casos de uso concretos. Los casos de uso inyectan puertos de salida y sus colaboradores de reglas y validación.

Otro caso de uso puede llamar un puerto de entrada cuando realmente necesite esa operación completa, sin ciclos entre casos de uso. No crear esa dependencia solo para reutilizar una validación.

La inyección se hace por constructor, con dependencias `final`; puede usarse `@RequiredArgsConstructor`. Por decisión posterior del proyecto, los casos de uso se registran mediante `@Service` y se inyectan por su interfaz. `ProcessApplicationUseCase` recibe `CustomerPort`, `ApplicationPort`, `TransactionPort`, `ApplicationValidator` y `ApprovalRules`; `QueryApplicationsUseCase` recibe `ApplicationPort` y `ApplicationValidator`. El validador se registra con `@Component`. `infrastructure/configuration` registra las reglas puras y la configuración transaccional mediante `@Bean`.

**Límites:** dominio no importa aplicación ni infraestructura. Aplicación no importa infraestructura; se permite `@Service` para registrar casos de uso, pero no APIs de persistencia o transacciones de Spring. Infraestructura puede importar las capas internas. El paquete `exception` no depende de ellas. Se acepta Reactor en los puertos y en aplicación; los modelos y las reglas no necesitan Reactor.

## 5. Transacción y recorrido de una solicitud

Aplicación decide **qué operaciones deben ir juntas**; infraestructura sabe **cómo ejecutar la transacción**.

Contrato de `TransactionPort`:

```java
<T> Mono<T> execute(Supplier<Mono<T>> operation);
```

El adaptador usa `TransactionalOperator` sobre el `Mono` creado con `Mono.defer(operation)`. La configuración usa `R2dbcTransactionManager`, la misma `ConnectionFactory` de los repositorios y aislamiento `READ_COMMITTED`. No poner `TransactionalOperator` ni `@Transactional` en los casos de uso. [^transacciones]

Implementación actual: `R2dbcTransactionAdapter` utiliza `TransactionalOperator.execute(status -> Mono.defer(operation)).singleOrEmpty()` para conservar el resultado hasta completar la transacción y no emitirlo si falla el commit. `PersistenceConfiguration` registra el gestor y el operador transaccional.

### Camino principal

1. El handler interpreta el JSON, lo convierte a `ApplicationData` y llama al puerto de entrada.
2. Aplicación valida que estén presentes la referencia, el cliente, el monto y el plazo. Referencia y cliente no pueden estar en blanco.
3. Busca la referencia: si ya existe, compara cliente, monto y plazo. Devuelve el original si coinciden; informa conflicto si cambian. No vuelve a evaluar.
4. Para una referencia nueva, abre la transacción y obtiene el cliente con bloqueo. Después consulta el total aprobado, en una consulta separada.
5. Evalúa, construye la solicitud aprobada o rechazada e inserta el resultado con su fecha. Si el cliente no existe, sigue el tratamiento de rechazo de la sección 6.
6. Confirma la transacción y solo entonces entrega el resultado al handler.

El bloqueo se implementa con `SELECT ... FOR UPDATE` y se conserva hasta finalizar la transacción. La consulta posterior del total, con `READ_COMMITTED`, puede ver lo confirmado por el procesamiento anterior. **Todas las rutas que aprueben créditos deben respetar este orden.** [^bloqueos] [^aislamiento]

El cupo consumido corresponde a la suma de montos aprobados. Los rechazos no lo aumentan. Dos solicitudes de 6 millones para un cliente con cupo libre de 10 millones deben dejar una aprobada y otra rechazada.

### Misma referencia al mismo tiempo

PostgreSQL debe imponer una referencia única; una consulta previa no reemplaza esa protección. [^unicidad]

Cuando la inserción pierda esa carrera, el adaptador emite `DuplicateReferenceException`. La aplicación la recupera **fuera de la transacción fallida, después de su reversión**: consulta el original y compara sus datos. No consulta dentro de una transacción abortada ni modifica el original.

La comparación del monto es numérica: `6000000` y `6000000.00` representan el mismo valor. También se conserva el resultado original cuando fue un rechazo.

**Reglas reactivas del proyecto:** componer una sola cadena; no usar `.block()` ni `.subscribe()` manual en el flujo de peticiones. No paralelizar las operaciones de esta transacción ni convertir errores técnicos en resultados exitosos. No añadir reintentos generales sin definir qué errores admiten recuperación.

## 6. Validaciones, excepciones y respuestas

**Una solicitud rechazada no es lo mismo que una petición imposible de interpretar o un fallo técnico.** El enunciado exige conservar aprobaciones y rechazos. [^prueba]

| Situación | Tratamiento definido |
|---|---|
| JSON ilegible o tipos incompatibles | Error HTTP 400 en la entrada; no se procesa una solicitud. |
| Campos obligatorios ausentes o referencia/cliente en blanco | `InvalidApplicationDataException` en aplicación; HTTP 400. |
| Cliente inexistente durante el procesamiento | `CustomerNotFoundException` en aplicación; se transforma en rechazo y se guarda. |
| Monto no positivo, plazo fuera de 6–60, cliente bloqueado o cupo insuficiente | `CreditDecision` rechazada con motivo; se guarda, sin consumir cupo. |
| Referencia existente con datos diferentes | `ReferenceConflictException`; HTTP 409, original intacto. |
| Consulta por referencia inexistente | `ApplicationNotFoundException`; HTTP 404. |
| Colisión al insertar la misma referencia | `DuplicateReferenceException` en persistencia; recuperación indicada en sección 5. |
| Fallo de acceso o de transacción | `PersistenceFailureException`; HTTP 500 con mensaje público genérico. Nunca convertirlo en rechazo de crédito. |

`CustomerNotFoundException` se captura dentro de la operación de aplicación, antes de finalizar la transacción, para construir e insertar el rechazo. Una consulta vacía no es una caída de la base de datos. Esta excepción no debe llegar al manejador HTTP como un 404. La persistencia deberá permitir conservar el `customerId` recibido aunque ese cliente no exista.

El dominio devuelve motivos estables, por ejemplo `INVALID_AMOUNT`, `INVALID_TERM`, `CUSTOMER_BLOCKED` e `INSUFFICIENT_LIMIT`. El rechazo por ausencia de cliente usa `CUSTOMER_NOT_FOUND`.

En los adaptadores, usar `onErrorMap` para traducir errores técnicos conocidos. En aplicación, usar `onErrorResume` para recuperaciones específicas. `doOnError` permite observar o registrar el error, pero no lo resuelve. [^reactor]

No traducir cualquier restricción violada como referencia duplicada: comprobar que sea la restricción de referencia. No envolver una excepción propia de nuevo como fallo genérico.

`GlobalErrorHandler`, en infraestructura HTTP, implementa `WebExceptionHandler` y centraliza la respuesta de error. [^erroresweb] Responde con `code`, `message` y `traceId`. Registra una vez los fallos técnicos con su causa y correlación; no expone SQL, credenciales ni trazas al navegador.

Implementación actual en `infrastructure/routerhandler/error`, con `@Order(-2)` y serialización mediante el `ObjectMapper` de Jackson 3 de Spring Boot. `ErrorResponseDto` usa builder y `@JsonProperty`; los mensajes públicos están en español y la cabecera `X-Trace-Id` contiene la misma correlación del cuerpo. Los errores HTTP de lectura y transporte mantienen sus estados; los errores propios se traducen a 400, 404, 409 o 500 según su origen.

### API prevista

```text
POST /applications
GET  /applications/{reference}
GET  /applications?limit=20
```

Una solicitud nueva persistida devuelve **201**, aprobada o rechazada. Una repetición idéntica devuelve **200**, con el resultado original. Las consultas exitosas devuelven **200**. El handler elige el estado HTTP; dominio no contiene códigos HTTP.

La respuesta de solicitud incluye los cuatro datos de entrada, `status`, `message`, `processedAt` y, para rechazos, `reasonCode` y `reason`. Usar `APPROVED` y `REJECTED` como estados. `ApplicationDtoMapper.toResponse(ProcessingResult)` añade el mensaje de POST: «Esta solicitud fue aprobada/rechazada» para solicitudes nuevas y «Esta solicitud ya fue aprobada/rechazada» para reintentos idénticos. Las consultas muestran «Esta solicitud fue aprobada/rechazada». El mensaje se construye al responder, sin persistirlo ni modificar la decisión original. El límite de recientes será 20 por defecto, con rango permitido de 1 a 100 y orden del más reciente al más antiguo; validar ese parámetro manualmente.

## 7. Docker y frontend

### Ejecución local

El repositorio contendrá `backend/`, `frontend/`, `docs/`, un `compose.yaml` y `.env.example`. Backend y frontend tendrán su propio Dockerfile. Las credenciales reales no se guardan en Git.

Servicios previstos: `frontend`, `backend` y `postgres`, con un volumen persistente para PostgreSQL. La cola no se arranca mientras siga fuera del alcance.

El backend se conecta a `postgres:5432` por la red de Compose, no a `localhost`. Los servicios de Compose pueden localizarse por su nombre dentro de esa red. [^dockerred]

Propuesta para el navegador: servir Angular y reenviar `/api/*` desde el contenedor frontend hacia `backend:8080`, quitando `/api`. Así Angular llama a `/api/applications`; no necesita resolver el nombre interno `backend`.

Configurar comprobación de salud de PostgreSQL y dependencia del backend con `condition: service_healthy`. El orden de arranque por sí solo no garantiza que la base ya acepte conexiones. [^dockerinicio]

Objetivo de ejecución cuando se implemente: `docker compose up --build`.

### Organización visual de Angular

Usar `ui/atoms` para botones e inputs; `ui/molecules` para campos con etiqueta y error; `ui/organisms` para formulario y listado; `ui/templates` para distribución; `pages` para pantallas completas. Estas categorías corresponden al diseño atómico. [^atomico]

Las llamadas HTTP van en servicios de `api/`, no en componentes visuales pequeños. El backend conserva la autoridad sobre las aprobaciones. Ante un reintento de envío, el frontend conserva la misma referencia.

## 8. Verificación y siguientes límites

| Prueba por implementar | Qué debe demostrar |
|---|---|
| Reglas y validaciones manuales | Aprobaciones, rechazos y límites correctos; distinguir datos ausentes de datos rechazables. |
| Concurrencia contra PostgreSQL real | Nunca superar el cupo con solicitudes simultáneas. |
| Referencias repetidas, incluso simultáneas | Un registro, sin doble consumo y sin cambios en el original; incluir referencias rechazadas y cambios de cliente. |
| Errores y transacciones | Rechazo persistido para cliente inexistente; reversión ante fallos; ninguna respuesta de éxito antes de confirmar. |
| HTTP y mappers | Contratos y estados correctos; ninguna entidad R2DBC expuesta por la API. |
| Dependencias y arranque local | Respetar las capas e iniciar frontend, backend y base desde Compose. |

**Pendiente:** modelar tablas, restricciones e índices; definir migraciones, versiones compatibles y configuración final. No inventar nuevas reglas de aprobación mientras se implementa esta arquitectura.

**Colas, solo después:** agregar un puerto de salida y su adaptador para publicar eventos. Un consumidor entrante llamaría a un puerto de entrada, no a un repositorio. Antes de implementarlos, definir cómo evitar perder eventos entre confirmar en PostgreSQL y publicar en la cola. No asumir que la transacción R2DBC incluye al broker. RabbitMQ es opcional en el enunciado. [^prueba-opcional]

---

## Referencias

El enunciado define los requisitos del negocio. Las fuentes técnicas respaldan el comportamiento de las herramientas; no imponen esta organización de carpetas o contratos.

[^prueba]: `Prueba_Tecnica_Full_Stack_Java_Spring_Boot_WebFlux_Angular_Core_Creditos.pdf`, página 2, RF02–RF07 y restricciones técnicas. Los datos de entrada y la fecha se describen en la página 1.
[^prueba-opcional]: Mismo enunciado, página 3, sección 9: RabbitMQ opcional.
[^webflux]: Spring Framework, Functional Endpoints: `https://docs.spring.io/spring-framework/reference/web/webflux-functional.html`
[^repositorios]: Spring Data R2DBC, interfaz R2dbcRepository: `https://docs.spring.io/spring-data/r2dbc/docs/current/api/org/springframework/data/r2dbc/repository/R2dbcRepository.html`
[^transacciones]: Spring Framework, Programmatic Transaction Management: `https://docs.spring.io/spring-framework/reference/data-access/transaction/programmatic.html`
[^bloqueos]: PostgreSQL, Explicit Locking, bloqueos por fila: `https://www.postgresql.org/docs/current/explicit-locking.html`
[^aislamiento]: PostgreSQL, Transaction Isolation, Read Committed: `https://www.postgresql.org/docs/current/transaction-iso.html`
[^unicidad]: PostgreSQL, Index Uniqueness Checks: `https://www.postgresql.org/docs/current/index-unique-checks.html`
[^reactor]: Project Reactor, Handling Errors: `https://projectreactor.io/docs/core/release/reference/coreFeatures/error-handling.html`
[^erroresweb]: Spring Framework, WebExceptionHandler: `https://docs.spring.io/spring-framework/docs/current/javadoc-api/org/springframework/web/server/WebExceptionHandler.html`
[^dockerred]: Docker, Networking in Compose: `https://docs.docker.com/compose/how-tos/networking/`
[^dockerinicio]: Docker, Control startup and shutdown order in Compose: `https://docs.docker.com/compose/how-tos/startup-order/`
[^atomico]: Brad Frost, Atomic Design Methodology: `https://atomicdesign.bradfrost.com/chapter-2/`
