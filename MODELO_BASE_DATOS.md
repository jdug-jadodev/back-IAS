# Modelo de base de datos — Core de créditos

**Estado:** diseño para implementar. Las migraciones y las pruebas están pendientes.

**Objetivo:** conservar clientes, solicitudes y decisiones de crédito; impedir duplicados y proteger el cupo ante solicitudes simultáneas.

## 1. Decisión principal

**Qué haremos:** crearemos dos tablas de negocio, `customers` y `credit_applications`.

| Tabla | Por qué la separamos | Para qué servirá |
|---|---|---|
| `customers` | Los datos actuales del cliente son distintos de sus solicitudes históricas. | Conservar clientes conocidos, habilitación y cupo máximo. |
| `credit_applications` | Cada solicitud tiene sus propios datos y una decisión final que debe conservarse. | Consultar aprobaciones y rechazos y recuperar el resultado original de un reintento. |

Un cliente puede tener cero o muchas solicitudes. Una solicitud se vincula con cero o un cliente conocido: el vínculo queda vacío cuando se rechazó porque el cliente no existía. La identificación recibida siempre se conserva en otra columna.

No crearemos tablas de pagos, cuotas, intereses, estados, rechazos, intentos HTTP o colas en esta etapa. Mantendremos el alcance en procesar y consultar decisiones de crédito, sin añadir procesos de administración posterior.

## 2. Tabla `customers`

| Columna | Tipo | Obligatoria | Función |
|---|---|---|---|
| `customer_id` | `TEXT`, clave primaria | Sí | Identificador conocido, por ejemplo `CLI-1001`. |
| `status` | `TEXT` | Sí | Solo `ELIGIBLE` o `BLOCKED`. |
| `approval_limit` | `NUMERIC` | Sí | Cupo máximo acumulado en COP; finito y no negativo. |

La clave primaria identifica de manera única una fila y no admite nulos. [^restricciones] Usaremos directamente el identificador estable del cliente para reconocerlo en consultas y relaciones. No se renombrará ni se reutilizará. No construiremos un módulo para administrar clientes en esta etapa.

No añadiremos nombre, correo, documento personal o contraseña: no se utilizan para tomar estas decisiones de crédito y aumentarían los datos que debemos gestionar.

### Datos semilla

| customer_id | status | approval_limit |
|---|---|---:|
| CLI-1001 | ELIGIBLE | 10000000 |
| CLI-1002 | BLOCKED | 8000000 |
| CLI-2001 | ELIGIBLE | 15000000 |

Cargaremos estos tres clientes para disponer de casos habilitados, un caso bloqueado y distintos cupos durante el desarrollo y las pruebas. Una base nueva comenzará sin solicitudes. Reiniciar los contenedores no borrará las solicitudes existentes.

## 3. Tabla `credit_applications`

Una fila representa una solicitud con decisión final. No representa cada clic, petición HTTP ni reintento.

| Columna | Tipo | Obligatoria | Función |
|---|---|---|---|
| `id` | `BIGINT GENERATED ALWAYS AS IDENTITY`, clave primaria | Sí, generado | Identificador interno. |
| `application_reference` | `TEXT`, único | Sí | Referencia pública e identificación de reintentos. |
| `requested_customer_id` | `TEXT` | Sí | El `customerId` recibido; también se conserva si el cliente no existe. |
| `customer_id` | `TEXT`, clave foránea a `customers.customer_id` | No | Cliente realmente identificado durante el procesamiento. |
| `amount` | `NUMERIC` | Sí | Monto recibido, sin redondearlo. Puede ser cero o negativo en un rechazo. |
| `term_months` | `INTEGER` | Sí | Plazo recibido; puede quedar fuera de 6–60 en un rechazo. |
| `status` | `TEXT` | Sí | Solo `APPROVED` o `REJECTED`. |
| `reason_code` | `TEXT` | Solo en rechazos | Motivo estable para el programa. |
| `reason` | `TEXT` | Solo en rechazos | Explicación segura para la persona. |
| `processed_at` | `TIMESTAMPTZ` | Sí, generado | Momento de registrar la decisión. |

`IDENTITY` genera el identificador interno; la clave primaria añade su unicidad. El `id` no reemplaza la referencia ni se usa como contador de solicitudes: puede tener saltos. [^identidad] Para este proyecto, separar identidad interna y referencia externa hace explícita la responsabilidad de cada una. Usar la referencia como clave primaria también sería viable, pero no es la opción elegida.

Conservaremos los identificadores sin cambios silenciosos de mayúsculas o formato, para no alterar qué cliente o referencia se solicitó. `REF-001` y `CLI-1001` son ejemplos, no formatos obligatorios. No fijaremos aquí una longitud de negocio; los límites técnicos de tamaño de entrada se documentarán en HTTP al implementar.

## 4. La relación y el cliente inexistente

**Relación:** `credit_applications.customer_id` apunta a `customers.customer_id`.

Una clave foránea comprueba que un vínculo no vacío apunte a un cliente existente. [^restricciones]

| Caso | requested_customer_id | customer_id | Resultado |
|---|---|---|---|
| Cliente conocido | CLI-1001 | CLI-1001 | Se evalúan las reglas. |
| Cliente bloqueado | CLI-1002 | CLI-1002 | Rechazo `CUSTOMER_BLOCKED`, si los demás datos evaluados son válidos. |
| Cliente desconocido | CLI-9999 | NULL | Rechazo `CUSTOMER_NOT_FOUND`. |

Si solo guardáramos una clave foránea obligatoria, no podríamos conservar la solicitud de un cliente inexistente. Si solo guardáramos un vínculo opcional, perderíamos el identificador recibido cuando fuera nulo.

Las dos columnas representan hechos distintos: **lo que se solicitó** y **qué cliente pudo identificarse al decidir**. No creamos clientes ficticios ni quitamos la relación.

Reglas del vínculo:

- Con cliente identificado, `customer_id` debe ser igual a `requested_customer_id`.
- Sin cliente identificado, se conserva `requested_customer_id`, se deja `customer_id = NULL` y se registra `REJECTED / CUSTOMER_NOT_FOUND`.
- Una solicitud aprobada nunca puede tener un vínculo nulo.

Estas comprobaciones de coherencia no demuestran por sí solas que un cliente estaba ausente en el instante de evaluación: esa consulta corresponde al caso de uso.

Si el cliente aparece en el futuro, no se rellena retroactivamente el vínculo ni se cambia el rechazo original. Un reintento de esa referencia sigue devolviendo la decisión original.

**Borrado:** usar `ON DELETE RESTRICT`, sin borrado en cascada de solicitudes. Evitar también renombrar identificadores referenciados. No existe una operación de borrado de clientes en este alcance. [^restricciones]

**API:** `customerId` de entrada y salida corresponde siempre a `requested_customer_id`. El vínculo interno opcional no reemplaza el dato que ve Angular.

## 5. Dinero y fechas

### Dinero exacto, sin redondeo automático

Trabajaremos con montos en COP. Queremos conservar el valor recibido sin redondearlo silenciosamente, tanto al guardar como al comprobar un reintento.

Usaremos `NUMERIC` sin precisión ni escala declaradas, acompañado de `BigDecimal` en el backend. PostgreSQL lo trata como decimal exacto y no fuerza todos los valores a una escala fija, dentro de sus límites de implementación. [^numeros]

No elegir `FLOAT` ni `DOUBLE PRECISION` para los montos. Tampoco redondear automáticamente con `NUMERIC(19,2)`: una escala fija podría cambiar un valor antes de conservarlo y compararlo en un reintento.

Aceptar solo decimales finitos: excluir `NaN`, `Infinity` y `-Infinity` en los valores persistidos. Esta es una condición técnica de representación, no un nuevo criterio de aprobación. La API limitará el tamaño de las entradas antes del procesamiento; `NUMERIC` no significa recursos ilimitados. Los límites concretos de lectura se fijarán al implementar.

### Transporte del monto entre Angular y el backend

Usaremos texto decimal en la comunicación con Angular para conservar el monto sin depender de conversiones a números de coma flotante. La base seguirá almacenándolo como un valor numérico.

- Angular mantiene y envía `amount` como texto decimal, por ejemplo `"6000000.00"`, sin separadores de miles.
- El backend admite texto decimal y número JSON válido para aceptar ambos formatos de entrada. La conversión es directa a `BigDecimal`, nunca mediante `double`.
- La API devuelve `amount` como texto decimal en POST y consultas. Actualizar los DTO, mappers, modelos y parser de Angular de forma coordinada.
- El almacenamiento sigue siendo numérico: no guardar dinero como `TEXT` en PostgreSQL.

La finalidad es no depender de la precisión de `Number` de JavaScript para conservar y reenviar el monto. [^javascript]

En reintentos, comparar por valor numérico: `6000000`, `6000000.0` y `6000000.00` son el mismo monto. En Java usar comparación numérica de `BigDecimal`; en Angular centralizar la normalización/comparación de textos decimales sin convertirlos a `Number`. Formatear para mostrar no debe modificar el valor de transporte.

### Fecha de procesamiento

Usar `processed_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp()`. PostgreSQL conserva el instante internamente en UTC; no conserva el nombre original de la zona horaria. [^fechas]

`clock_timestamp()` registra la hora efectiva de su evaluación, en lugar de la hora de inicio de la transacción que devuelve `CURRENT_TIMESTAMP`. Es útil cuando hubo espera por un bloqueo. [^reloj]

La fecha representa el momento de registrar la decisión, no el instante exacto del commit. Recuperar el valor persistido mediante `INSERT ... RETURNING`, mapearlo a `Instant` en dominio y devolverlo solo después del commit. Así la primera respuesta y las consultas utilizan el mismo dato almacenado.

## 6. Responsabilidades y protección de los datos

**La decisión del crédito se toma en el código del backend, antes de guardar. La base no es un segundo motor de aprobación.**

| Responsable | Qué hará | Para qué |
|---|---|---|
| Dominio | Evaluar las reglas puras y construir la decisión con su motivo. | Decidir si la solicitud se aprueba o se rechaza. |
| Aplicación | Validar la entrada, consultar los datos, coordinar las reglas y pedir el guardado. | Completar el procesamiento dentro de la transacción. |
| PostgreSQL | Aplicar relaciones, unicidad y comprobaciones de coherencia al escribir. | Impedir duplicados y registros contradictorios. |

Añadiremos `CHECK` condicionales como defensa adicional ante errores de implementación. **No sustituirán las validaciones del backend.**

Ejemplo: `amount = 0` con `REJECTED / INVALID_AMOUNT` es un resultado coherente que guardaremos. `amount = 0` con `APPROVED` es una contradicción que impediremos guardar. El `CHECK` no transforma esa aprobación en rechazo ni genera un motivo; el fallo se tratará como un error técnico de consistencia.

No pondremos un `CHECK (amount > 0)` general ni un `CHECK (term_months BETWEEN 6 AND 60)` general en todas las filas: impedirían conservar rechazos por monto o plazo inválido. La protección será condicional: **si está aprobada, debe cumplir esas condiciones**.

| Protección en la base | Qué contradicción o problema impide |
|---|---|
| Identificadores y datos obligatorios no nulos; identificadores no blancos | No almacenar solicitudes sin los datos mínimos interpretables. |
| Referencia única en toda la tabla | No crear dos solicitudes con la misma referencia, aunque sean de clientes distintos. |
| Clave foránea opcional y vínculo coherente | No relacionar una solicitud con un cliente distinto del recibido. |
| Estados permitidos | No introducir estados desconocidos o pendientes no modelados. |
| Aprobada: cliente vinculado, monto positivo, plazo 6–60 | No persistir una aprobación con contradicciones básicas. |
| Aprobada: motivo y código nulos | No almacenar aprobación y motivo de rechazo simultáneamente. |
| Rechazada: motivo y código no nulos ni blancos | Conservar una explicación utilizable. |
| Código `CUSTOMER_NOT_FOUND` y vínculo nulo coherentes | Conservar el caso de cliente desconocido explícitamente. |
| Montos finitos y cupo máximo no negativo | Mantener una representación monetaria coherente. |

**Costo de esta defensa:** algunas condiciones estarán tanto en el código como en SQL. Cuando cambien, actualizaremos ambas y sus pruebas; no copiaremos todo el motor de aprobación a la base. El cupo acumulado se protegerá con el flujo transaccional de la sección 8, no con un `CHECK` que intente sumar otras filas.

Códigos iniciales: `INVALID_AMOUNT`, `INVALID_TERM`, `CUSTOMER_BLOCKED`, `INSUFFICIENT_LIMIT`, `CUSTOMER_NOT_FOUND`. Guardaremos el código y el mensaje utilizados al decidir para conservar la explicación histórica, sin reconstruirla con un catálogo actual.

Usar texto con restricciones de valores permitidos, no tablas de catálogos ni un tipo ENUM específico de PostgreSQL en esta versión.

Al escribir el SQL, combinar `NOT NULL` y condiciones explícitas: en PostgreSQL un `CHECK` cuyo resultado sea nulo no falla. Los `CHECK` no sustituyen la validación que involucra otras filas. [^restricciones]

JSON ilegible, campos obligatorios ausentes o valores imposibles de representar se tratarán como errores de entrada, sin registrar una decisión de crédito. Una caída de la base será un error técnico, no una fila con `REJECTED`.

## 7. Cupo: un solo origen para el total aprobado

No crear `used_limit` ni `available_limit` en `customers` inicialmente.

```text
cupo usado = suma de amount de las solicitudes APPROVED del cliente
cupo disponible = approval_limit - cupo usado
```

La ausencia de aprobaciones equivale a cero. Los rechazos quedan fuera de la suma.

Ejemplo ilustrativo: con cupo de 10 millones, una aprobación por 6 millones deja 4 millones. Un rechazo posterior por otros 6 millones mantiene esos 4 millones disponibles.

**Motivo:** evitamos mantener un contador y un historial que puedan discrepar si un proceso actualiza uno y olvida el otro. **Costo:** recalcular la suma requiere consultar aprobaciones; revisar esta decisión con mediciones si el volumen crece.

No modelaremos liberación de cupo por pagos o cancelaciones: esta etapa conservará decisiones finales de solicitudes, sin administrar su vida posterior.

## 8. Concurrencia e idempotencia

Usaremos un turno por cliente dentro de una transacción para impedir que dos solicitudes decidan con el mismo cupo disponible. La estructura de tablas no basta por sí sola: todos los caminos que aprueben ejecutarán el siguiente procedimiento.

### Solicitudes nuevas de un mismo cliente

1. Buscar una referencia existente y resolver el reintento antes de reevaluar.
2. Abrir la transacción mediante `TransactionPort` / `TransactionalOperator`.
3. Obtener al cliente con `SELECT ... FOR UPDATE`.
4. Después del bloqueo, consultar la suma aprobada en otra sentencia, con `READ COMMITTED`.
5. Evaluar y hacer un `INSERT` del resultado final, aprobado o rechazado.
6. Confirmar; después entregar el resultado.

El bloqueo permanece hasta finalizar la transacción. La consulta posterior, separada, puede ver lo confirmado por el procesamiento anterior. No unir lectura previa de cupo y bloqueo en una lectura que conserve datos viejos. [^bloqueos] [^aislamiento]

Si no existe el cliente, no hay fila que bloquear; se conserva su rechazo y la referencia única protege los duplicados.

### Referencias repetidas

Nombre de la restricción: `uq_credit_applications_reference`.

La unicidad es global, no por la pareja cliente–referencia. Una repetición idéntica devuelve lo guardado; datos diferentes generan conflicto. No cambiar `processed_at`, estado, motivo ni monto del original.

Si dos inserciones compiten por una referencia, identificaremos específicamente esa restricción. Revertiremos la transacción que perdió y recuperaremos el original fuera de la transacción fallida, para resolver el reintento sin modificar lo ya procesado. No usaremos `ON CONFLICT DO UPDATE` para sobrescribir el resultado.

## 9. Índices mínimos

Además de las claves primarias, el diseño usa:

| Índice | Uso |
|---|---|
| El índice de la restricción única sobre `application_reference` | Buscar una referencia y proteger duplicados. |
| `(customer_id, status)` | Consultar aprobaciones de un cliente y localizar solicitudes vinculadas. |
| `(processed_at DESC, id DESC)` | Consultar recientes con desempate estable. |

Los índices compuestos se eligen según los filtros y el orden de las consultas. No añadir un índice por columna ni repetir el índice de una restricción única. [^indices] [^orden]

Orden del listado: `ORDER BY processed_at DESC, id DESC LIMIT ...`. Esto es un orden estable por la fecha guardada, no una garantía de orden exacto de commits. El límite de la API será 20 por defecto, con valores permitidos de 1 a 100, para acotar el tamaño de cada consulta.

## 10. Integración con la arquitectura

`CustomerEntity` y `ApplicationEntity` viven en infraestructura. Dominio mantiene modelos propios. No agregar anotaciones JPA, relaciones cargadas automáticamente ni una lista de solicitudes dentro de cada entidad de cliente.

R2DBC mapeará columnas; los adaptadores harán las consultas explícitas necesarias. [^r2dbc]

El caso de uso ya conoce si encontró al cliente. Esa información debe llegar al modelo que se guarda como vínculo opcional, además del identificador solicitado. El mapper solo convierte datos: no consulta clientes ni vuelve a decidir si existen.

El mapper hacia la API toma `customerId` de `requested_customer_id`. Los detalles de la clave foránea no se filtran al formulario.

El guardado significa insertar, nunca actualizar un resultado anterior. Las solicitudes finales serán inmutables desde los casos de uso. Para reforzarlo en el despliegue, no conceder UPDATE/DELETE sobre esa tabla al usuario de ejecución cuando se separen los permisos de migraciones. Las restricciones descritas no vuelven inmutable una tabla por sí solas ni protegen frente a un administrador que escriba directamente saltándose el protocolo.

## 11. Migraciones y Docker

Preparar cambios versionados, por ejemplo:

```text
V1__create_credit_schema.sql
V2__seed_customers.sql
```

Crearemos primero tablas, restricciones e índices; después cargaremos los tres clientes semilla. Versionaremos estos cambios para repetir la instalación de forma controlada. No recrearemos ni vaciaremos tablas en cada arranque ni sobrescribiremos datos existentes al cargar semillas. Usaremos un volumen persistente de PostgreSQL para conservar los datos al reiniciar los contenedores.

La herramienta de migraciones y los permisos se concretarán al implementar; este documento no implica que estén configurados. Frontend no se conecta a PostgreSQL. Base y credenciales permanecen detrás del backend.

## 12. Verificación antes de dar el modelo por terminado

| Caso | Resultado esperado |
|---|---|
| Cliente conocido y datos válidos | Fila aprobada y vinculada. |
| Cliente inexistente | Rechazo con identificador solicitado conservado y vínculo nulo. |
| Monto cero/negativo o plazo inválido | Rechazo persistido; no falla una restricción positiva general. |
| Aprobación con monto cero, plazo inválido o sin vínculo | La base rechaza esa escritura incoherente. |
| Vínculo a cliente inexistente o distinto del solicitado | Error de integridad. |
| Dos solicitudes de 6 millones con cupo de 10 millones | Una aprobada y otra rechazada, total aprobado 6 millones. |
| Referencia repetida, incluso entre clientes distintos | Un solo registro; original intacto; recuperación o conflicto. |
| Mismo monto con distinta cantidad de ceros decimales | Reintento equivalente, no conflicto artificial. |
| Caída durante el guardado | Sin resultado parcial ni aprobación falsa. |
| Reinicio de Docker | Solicitudes previas conservadas. |

Estas pruebas se ejecutarán contra PostgreSQL real para validar restricciones y concurrencia. No se han ejecutado como parte de este documento.

## 13. Qué no estamos guardando todavía

No hay auditoría completa de cada paso, historial de cambios del cliente ni valores históricos de cupo/estado usados para cada evaluación. Guardaremos la decisión y su motivo para explicar el resultado de cada solicitud. Una futura auditoría que deba reconstruir todas las condiciones necesitaría guardar esos datos de evaluación, no consultar los valores actuales del cliente.

No hay tabla de outbox ni mensajes hasta decidir incorporar la cola. No añadirlas vacías por anticipación.

---

## Referencias técnicas

[^restricciones]: PostgreSQL, Constraints: `https://www.postgresql.org/docs/current/ddl-constraints.html`
[^identidad]: PostgreSQL, Identity Columns: `https://www.postgresql.org/docs/current/ddl-identity-columns.html`
[^numeros]: PostgreSQL, Numeric Types: `https://www.postgresql.org/docs/current/datatype-numeric.html`
[^fechas]: PostgreSQL, Date/Time Types: `https://www.postgresql.org/docs/current/datatype-datetime.html`
[^reloj]: PostgreSQL, Date/Time Functions and Operators: `https://www.postgresql.org/docs/current/functions-datetime.html`
[^bloqueos]: PostgreSQL, Explicit Locking: `https://www.postgresql.org/docs/current/explicit-locking.html`
[^aislamiento]: PostgreSQL, Transaction Isolation: `https://www.postgresql.org/docs/current/transaction-iso.html`
[^indices]: PostgreSQL, Multicolumn Indexes: `https://www.postgresql.org/docs/current/indexes-multicolumn.html`
[^orden]: PostgreSQL, Indexes and ORDER BY: `https://www.postgresql.org/docs/current/indexes-ordering.html`
[^r2dbc]: Spring Data Relational, R2DBC Mapping: `https://docs.spring.io/spring-data/relational/reference/r2dbc/mapping.html`
[^javascript]: ECMAScript Language Specification, Number Type: `https://tc39.es/ecma262/multipage/ecmascript-data-types-and-values.html#sec-ecmascript-language-types-number-type`
