# Arquitectura del core de créditos

**Estado:** diseño para implementar. No incluye código funcional ni el modelo de tablas.

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

## 2. Estructura del backend

Carpetas dentro del paquete Java principal. Los nombres de clases son los propuestos para la implementación.

```text
creditos/
├── CreditosApplication.java
├── dominio/
│   ├── modelo/
│   ├── regla/
│   │   └── ReglasAprobacion.java
│   └── puerto/
│       ├── entrada/
│       │   ├── ProcesarSolicitudPort.java
│       │   └── ConsultarSolicitudesPort.java
│       └── salida/
│           ├── ClientePort.java
│           ├── SolicitudPort.java
│           └── TransaccionPort.java
├── aplicacion/
│   ├── casouso/
│   │   ├── ProcesarSolicitudUseCase.java
│   │   └── ConsultarSolicitudesUseCase.java
│   ├── dto/
│   ├── mapper/
│   └── validacion/
├── infraestructura/
│   ├── entrada/http/
│   │   ├── SolicitudRouter.java
│   │   ├── SolicitudHandler.java
│   │   └── GlobalErrorHandler.java
│   ├── salida/r2dbc/
│   │   ├── adaptador/
│   │   ├── entidad/
│   │   ├── mapper/
│   │   └── repository/
│   ├── transaccion/
│   │   └── R2dbcTransaccionAdapter.java
│   └── configuracion/
└── excepciones/
    ├── aplicacion/
    └── basedatos/
```

No crear adaptadores vacíos para colas. Se incorporarán cuando exista esa integración.

## 3. Qué pertenece a cada capa

### Dominio: datos y reglas del crédito

Contiene modelos, reglas de aprobación y puertos de entrada y salida.

| Modelo conceptual | Contenido |
|---|---|
| `DatosSolicitud` | `applicationReference`, `customerId`, `amount`, `termMonths`. Representa lo recibido, todavía sin aprobar. |
| `Cliente` | Identificador, estado y cupo máximo. |
| `DecisionCredito` | Aprobación o rechazo, con motivo cuando corresponda. |
| `SolicitudCredito` | Datos de la solicitud, decisión y fecha de procesamiento. |
| `ResultadoProcesamiento` | Solicitud procesada y un indicador `nueva`, para distinguir creación de repetición. |

Usar `BigDecimal` para montos, `Integer` para el plazo recibido e `Instant` para la fecha. Son modelos del negocio, **no entidades de base de datos**.

`ReglasAprobacion` evalúa monto, plazo, habilitación y cupo con los datos que recibe. No consulta repositorios. `DatosSolicitud` debe permitir representar valores que luego producirán un rechazo; su constructor no debe descartarlos antes de evaluarlos.

**No habrá dos grupos de casos de uso.** Las reglas puras quedan en dominio; la coordinación del proceso queda en aplicación.

### Aplicación: coordinar el trabajo

Los casos de uso implementan los puertos de entrada. Validan manualmente, consultan puertos de salida, ejecutan las reglas y solicitan el guardado.

Aquí viven `SolicitudRequestDto`, `SolicitudResponseDto`, `ErrorResponseDto`, `SolicitudDtoMapper` y `SolicitudValidator`. Los DTO son contenedores de datos, sin validaciones automáticas ni lógica de aprobación.

**Los puertos del dominio no reciben DTO de aplicación.** El handler usa `SolicitudDtoMapper` para convertir el DTO a `DatosSolicitud`; luego llama al puerto. Así dominio no necesita importar aplicación.

### Infraestructura: conectar con tecnologías

Contiene HTTP, adaptadores R2DBC, entidades de base de datos, sus mappers, configuración de dependencias y transacciones.

Cada adaptador R2DBC implementa un puerto de salida e inyecta su repositorio técnico. Por ejemplo, `ClienteR2dbcAdapter` implementa `ClientePort` y usa `ClienteR2dbcRepository`.

La interfaz técnica extiende **`R2dbcRepository` de Spring Data**, no el driver PostgreSQL. El driver se configura como parte de la conexión. [^repositorios]

Los mappers de infraestructura convierten `ClienteEntity` y `SolicitudEntity` a modelos de dominio y viceversa. No validan reglas ni deciden aprobaciones.

### Excepciones: tipos compartidos, separados por origen

Este paquete reúne excepciones propias de aplicación y persistencia. Sus clases dependen solo de Java: no importan HTTP, Spring ni el driver. Pueden conservar la causa original como `Throwable`.

El adaptador interpreta el error técnico; aplicación decide las recuperaciones previstas; `GlobalErrorHandler` construye la respuesta HTTP. Las excepciones no escriben logs por sí mismas.

## 4. Contratos e inyección

**Dependemos de contratos, no de implementaciones concretas de casos de uso o adaptadores.**

```text
Router → Handler → ProcesarSolicitudPort
                         ↑ implementa
                ProcesarSolicitudUseCase
                         ↓ usa
             ClientePort / SolicitudPort
                         ↑ implementan
                Adaptadores R2DBC
                         ↓ usan
                Repositorios R2DBC
                         ↓
                     PostgreSQL
```

| Contrato | Operaciones previstas | Implementación |
|---|---|---|
| `ProcesarSolicitudPort` | `procesar(DatosSolicitud)` → `Mono<ResultadoProcesamiento>` | `ProcesarSolicitudUseCase` |
| `ConsultarSolicitudesPort` | `porReferencia(String)` → `Mono<SolicitudCredito>`; `recientes(int)` → `Flux<SolicitudCredito>` | `ConsultarSolicitudesUseCase` |
| `ClientePort` | `obtenerConBloqueo(String)` → `Mono<Cliente>`; vacío si no existe | `ClienteR2dbcAdapter` |
| `SolicitudPort` | Buscar por referencia, consultar total aprobado, insertar y listar recientes | `SolicitudR2dbcAdapter` |
| `TransaccionPort` | Ejecutar una operación como una sola transacción | `R2dbcTransaccionAdapter` |

`SolicitudPort` recibe y devuelve modelos de dominio o valores simples, nunca entidades R2DBC. `insertar` significa **crear sin sobrescribir**: no actualizar una solicitud existente.

El handler inyecta los puertos de entrada y el mapper de DTO. No inyecta puertos de persistencia ni casos de uso concretos. Los casos de uso inyectan puertos de salida y sus colaboradores de reglas y validación.

Otro caso de uso puede llamar un puerto de entrada cuando realmente necesite esa operación completa, sin ciclos entre casos de uso. No crear esa dependencia solo para reutilizar una validación.

La inyección se hace por constructor, con dependencias `final`; puede usarse `@RequiredArgsConstructor`. `infraestructura/configuracion` construye los casos de uso mediante `@Bean` y los expone por su interfaz. Las pruebas y esta configuración sí pueden conocer las implementaciones.

**Límites:** dominio no importa aplicación ni infraestructura. Aplicación no importa infraestructura ni Spring. Infraestructura puede importar las capas internas. El paquete `excepciones` no depende de ellas. Se acepta Reactor en los puertos y en aplicación; los modelos y las reglas no necesitan Reactor.

## 5. Transacción y recorrido de una solicitud

Aplicación decide **qué operaciones deben ir juntas**; infraestructura sabe **cómo ejecutar la transacción**.

Contrato de `TransaccionPort`:

```java
<T> Mono<T> ejecutar(Supplier<Mono<T>> operacion);
```

El adaptador usa `TransactionalOperator` sobre el `Mono` creado con `Mono.defer(operacion)`. La configuración usa `R2dbcTransactionManager`, la misma `ConnectionFactory` de los repositorios y aislamiento `READ_COMMITTED`. No poner `TransactionalOperator` ni `@Transactional` en los casos de uso. [^transacciones]

### Camino principal

1. El handler interpreta el JSON, lo convierte a `DatosSolicitud` y llama al puerto de entrada.
2. Aplicación valida que estén presentes la referencia, el cliente, el monto y el plazo. Referencia y cliente no pueden estar en blanco.
3. Busca la referencia: si ya existe, compara cliente, monto y plazo. Devuelve el original si coinciden; informa conflicto si cambian. No vuelve a evaluar.
4. Para una referencia nueva, abre la transacción y obtiene el cliente con bloqueo. Después consulta el total aprobado, en una consulta separada.
5. Evalúa, construye la solicitud aprobada o rechazada e inserta el resultado con su fecha. Si el cliente no existe, sigue el tratamiento de rechazo de la sección 6.
6. Confirma la transacción y solo entonces entrega el resultado al handler.

El bloqueo se implementa con `SELECT ... FOR UPDATE` y se conserva hasta finalizar la transacción. La consulta posterior del total, con `READ_COMMITTED`, puede ver lo confirmado por el procesamiento anterior. **Todas las rutas que aprueben créditos deben respetar este orden.** [^bloqueos] [^aislamiento]

El cupo consumido corresponde a la suma de montos aprobados. Los rechazos no lo aumentan. Dos solicitudes de 6 millones para un cliente con cupo libre de 10 millones deben dejar una aprobada y otra rechazada.

### Misma referencia al mismo tiempo

PostgreSQL debe imponer una referencia única; una consulta previa no reemplaza esa protección. [^unicidad]

Cuando la inserción pierda esa carrera, el adaptador emite `ReferenciaDuplicadaException`. La aplicación la recupera **fuera de la transacción fallida, después de su reversión**: consulta el original y compara sus datos. No consulta dentro de una transacción abortada ni modifica el original.

La comparación del monto es numérica: `6000000` y `6000000.00` representan el mismo valor. También se conserva el resultado original cuando fue un rechazo.

**Reglas reactivas del proyecto:** componer una sola cadena; no usar `.block()` ni `.subscribe()` manual en el flujo de peticiones. No paralelizar las operaciones de esta transacción ni convertir errores técnicos en resultados exitosos. No añadir reintentos generales sin definir qué errores admiten recuperación.

## 6. Validaciones, excepciones y respuestas

**Una solicitud rechazada no es lo mismo que una petición imposible de interpretar o un fallo técnico.** El enunciado exige conservar aprobaciones y rechazos. [^prueba]

| Situación | Tratamiento definido |
|---|---|
| JSON ilegible o tipos incompatibles | Error HTTP 400 en la entrada; no se procesa una solicitud. |
| Campos obligatorios ausentes o referencia/cliente en blanco | `DatosSolicitudInvalidosException` en aplicación; HTTP 400. |
| Cliente inexistente durante el procesamiento | `ClienteNoExisteException` en aplicación; se transforma en rechazo y se guarda. |
| Monto no positivo, plazo fuera de 6–60, cliente bloqueado o cupo insuficiente | `DecisionCredito` rechazada con motivo; se guarda, sin consumir cupo. |
| Referencia existente con datos diferentes | `ReferenciaEnConflictoException`; HTTP 409, original intacto. |
| Consulta por referencia inexistente | `SolicitudNoEncontradaException`; HTTP 404. |
| Colisión al insertar la misma referencia | `ReferenciaDuplicadaException` en persistencia; recuperación indicada en sección 5. |
| Fallo de acceso o de transacción | `FalloPersistenciaException`; HTTP 500 con mensaje público genérico. Nunca convertirlo en rechazo de crédito. |

`ClienteNoExisteException` se captura dentro de la operación de aplicación, antes de finalizar la transacción, para construir e insertar el rechazo. Una consulta vacía no es una caída de la base de datos. Esta excepción no debe llegar al manejador HTTP como un 404. La persistencia deberá permitir conservar el `customerId` recibido aunque ese cliente no exista.

El dominio devuelve motivos estables, por ejemplo `INVALID_AMOUNT`, `INVALID_TERM`, `CUSTOMER_BLOCKED` e `INSUFFICIENT_LIMIT`. El rechazo por ausencia de cliente usa `CUSTOMER_NOT_FOUND`.

En los adaptadores, usar `onErrorMap` para traducir errores técnicos conocidos. En aplicación, usar `onErrorResume` para recuperaciones específicas. `doOnError` permite observar o registrar el error, pero no lo resuelve. [^reactor]

No traducir cualquier restricción violada como referencia duplicada: comprobar que sea la restricción de referencia. No envolver una excepción propia de nuevo como fallo genérico.

`GlobalErrorHandler`, en infraestructura HTTP, implementa `WebExceptionHandler` y centraliza la respuesta de error. [^erroresweb] Responde con `code`, `message` y `traceId`. Registra una vez los fallos técnicos con su causa y correlación; no expone SQL, credenciales ni trazas al navegador.

### API prevista

```text
POST /applications
GET  /applications/{reference}
GET  /applications?limit=20
```

Una solicitud nueva persistida devuelve **201**, aprobada o rechazada. Una repetición idéntica devuelve **200**, con el resultado original. Las consultas exitosas devuelven **200**. El handler elige el estado HTTP; dominio no contiene códigos HTTP.

La respuesta de solicitud incluye los cuatro datos de entrada, `status`, `processedAt` y, para rechazos, `reasonCode` y `reason`. Usar `APPROVED` y `REJECTED` como estados. El límite de recientes será 20 por defecto, con rango permitido de 1 a 100 y orden del más reciente al más antiguo; validar ese parámetro manualmente.

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
