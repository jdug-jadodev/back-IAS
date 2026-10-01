# Entidades Java con Spring Data R2DBC y Lombok

Esta guía describe el mapeo del esquema real del proyecto a entidades Java. Los ejemplos usan Spring Data R2DBC, el driver R2DBC de PostgreSQL y Lombok.

## 1. Fuentes del modelo

- `db/init/01_create_schema.sql`: tablas, columnas, claves, restricciones e índices.
- `db/init/02_seed_customers.sql`: datos iniciales de clientes.
- `db/init/03_create_app_user.sh`: permisos del usuario utilizado por la aplicación.
- `compose.yaml`: PostgreSQL 17 y configuración de zona horaria UTC.

El esquema contiene dos tablas en `public`:

```text
customers
  customer_id (PK, TEXT)
       │
       └── credit_applications.customer_id (FK, nullable)

credit_applications
  id (PK, BIGINT generado)
  application_reference (UNIQUE)
  requested_customer_id (TEXT, sin FK)
```

Un cliente puede tener varias solicitudes. Una solicitud puede estar vinculada a un cliente existente o tener `customer_id = null`. `requested_customer_id` conserva el identificador recibido, incluso cuando el cliente no existe.

## 2. Dependencias

En un proyecto Maven con Spring Boot y su gestión de versiones, las dependencias necesarias son:

```xml
<dependencies>
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-data-r2dbc</artifactId>
    </dependency>

    <dependency>
        <groupId>org.postgresql</groupId>
        <artifactId>r2dbc-postgresql</artifactId>
        <scope>runtime</scope>
    </dependency>

    <dependency>
        <groupId>org.projectlombok</groupId>
        <artifactId>lombok</artifactId>
        <optional>true</optional>
    </dependency>
</dependencies>
```

Configura Lombok como procesador de anotaciones en el compilador y habilita su soporte en el IDE. En JDK 23 o posteriores, declara explícitamente el procesador de anotaciones en la configuración del compilador; no dependas de su descubrimiento automático. Utiliza versiones compatibles con el JDK elegido.

Ejemplo de conexión, con variables de entorno ya definidas para el proceso Java:

```yaml
spring:
  r2dbc:
    url: r2dbc:postgresql://${DB_HOST:localhost}:${POSTGRES_PORT:5432}/${POSTGRES_DB}
    username: ${APP_DB_USER}
    password: ${APP_DB_PASSWORD}
```

Docker Compose utiliza el archivo `.env`, pero eso no implica que Spring Boot lo cargue automáticamente. Proporciona esas variables al proceso Java. Si la aplicación corre dentro de la misma red de Compose, usa `DB_HOST=postgres` y el puerto interno `5432`.

## 3. Tipos y anotaciones

| PostgreSQL | Java | Observaciones |
|---|---|---|
| `TEXT` | `String` | Para identificadores y texto libre. |
| `TEXT` con lista de valores permitidos | `enum` | Los nombres de las constantes deben coincidir exactamente con los valores SQL. |
| `NUMERIC` | `BigDecimal` | Conserva precisión; evita `double` y `float` para importes. |
| `BIGINT` | `Long` | El identificador generado empieza como `null` en una entidad nueva. |
| `INTEGER` | `Integer` | Permite representar un campo aún no asignado; al persistir debe cumplir `NOT NULL`. |
| `TIMESTAMPTZ` | `OffsetDateTime` | Compatible con el driver PostgreSQL R2DBC. |

Imports de persistencia:

```java
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;
```

No se utilizan las anotaciones JPA `@Entity`, `@GeneratedValue`, `@ManyToOne`, `@OneToMany` ni `@Enumerated`. Spring Data R2DBC mapea las claves foráneas como valores; las consultas de relaciones se implementan explícitamente.

Para las columnas `TEXT`, el mapeo predeterminado de Spring Data R2DBC convierte los enums a su nombre y viceversa. No se necesita un enum nativo de PostgreSQL ni un convertidor personalizado para este esquema.

Las anotaciones `@Table` y `@Column` describen el mapeo; no crean las tablas ni sus restricciones. La fuente de verdad sigue siendo el script SQL.

## 4. Enums

Los paquetes de los ejemplos son orientativos. Crea cada enum y cada clase pública en su propio archivo.

### `CustomerStatus.java`

```java
package com.example.creditos.persistence.entity;

public enum CustomerStatus {
    ELIGIBLE,
    BLOCKED
}
```

### `CreditApplicationStatus.java`

```java
package com.example.creditos.persistence.entity;

public enum CreditApplicationStatus {
    APPROVED,
    REJECTED
}
```

### `ReasonCode.java`

```java
package com.example.creditos.persistence.entity;

public enum ReasonCode {
    INVALID_AMOUNT,
    INVALID_TERM,
    CUSTOMER_BLOCKED,
    INSUFFICIENT_LIMIT,
    CUSTOMER_NOT_FOUND
}
```

No hay valores `PENDING`, `IN_PROGRESS` ni similares en el esquema actual: cada fila representa una solicitud con resultado final.

## 5. Entidad de clientes

### Mapeo de `public.customers`

| Columna | Campo Java | Tipo Java | Nulo | Regla |
|---|---|---|---|---|
| `customer_id` | `customerId` | `String` | No | PK asignada externamente; `btrim(customer_id) <> ''`. |
| `status` | `status` | `CustomerStatus` | No | `ELIGIBLE` o `BLOCKED`. |
| `approval_limit` | `approvalLimit` | `BigDecimal` | No | Mayor o igual que cero y finito. Sin precisión o escala fija declarada. |

### `CustomerEntity.java`

```java
package com.example.creditos.persistence.entity;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(value = "customers", schema = "public")
public class CustomerEntity {

    @Id
    @Column("customer_id")
    private String customerId;

    @Column("status")
    private CustomerStatus status;

    @Column("approval_limit")
    private BigDecimal approvalLimit;
}
```

`customerId` no es un UUID ni un número autogenerado. Los registros iniciales son:

| customerId | status | approvalLimit |
|---|---|---|
| `CLI-1001` | `ELIGIBLE` | `10000000` |
| `CLI-1002` | `BLOCKED` | `8000000` |
| `CLI-2001` | `ELIGIBLE` | `15000000` |

El usuario de aplicación puede consultar clientes y actualizar únicamente `status`. Este permiso de actualización también permite el bloqueo con `SELECT ... FOR UPDATE`. No puede insertar o eliminar clientes ni modificar `approval_limit`.

Si en otro contexto se habilita la creación de clientes, un ID asignado y no nulo puede hacer que `save()` considere la entidad existente y ejecute un `UPDATE`. Para una creación explícita se puede usar `R2dbcEntityTemplate.insert(...)` o implementar una estrategia adecuada de detección de entidades nuevas.

## 6. Entidad de solicitudes de crédito

### Mapeo de `public.credit_applications`

| Columna | Campo Java | Tipo Java | Nulo | Regla |
|---|---|---|---|---|
| `id` | `id` | `Long` | No en BD | PK `GENERATED ALWAYS AS IDENTITY`; `null` antes del insert. |
| `application_reference` | `applicationReference` | `String` | No | Única y no vacía según `btrim`. |
| `requested_customer_id` | `requestedCustomerId` | `String` | No | No vacía según `btrim`; sin FK. |
| `customer_id` | `customerId` | `String` | Sí | FK a `customers.customer_id`; cuando existe, igual a `requested_customer_id`. |
| `amount` | `amount` | `BigDecimal` | No | Finito; debe ser positivo si la solicitud está aprobada. |
| `term_months` | `termMonths` | `Integer` | No | De 6 a 60, inclusive, si la solicitud está aprobada. |
| `status` | `status` | `CreditApplicationStatus` | No | `APPROVED` o `REJECTED`. |
| `reason_code` | `reasonCode` | `ReasonCode` | Sí | Código permitido; obligatorio para rechazadas. |
| `reason` | `reason` | `String` | Sí | Texto del rechazo; obligatorio y no vacío según `btrim` para rechazadas. |
| `processed_at` | `processedAt` | `OffsetDateTime` | No en BD | `DEFAULT clock_timestamp()` si se omite del insert. |

### `CreditApplicationEntity.java`

```java
package com.example.creditos.persistence.entity;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Table(value = "credit_applications", schema = "public")
public class CreditApplicationEntity {

    @Id
    @Column("id")
    private Long id;

    @Column("application_reference")
    private String applicationReference;

    @Column("requested_customer_id")
    private String requestedCustomerId;

    @Column("customer_id")
    private String customerId;

    @Column("amount")
    private BigDecimal amount;

    @Column("term_months")
    private Integer termMonths;

    @Column("status")
    private CreditApplicationStatus status;

    @Column("reason_code")
    private ReasonCode reasonCode;

    @Column("reason")
    private String reason;

    @Column("processed_at")
    private OffsetDateTime processedAt;
}
```

Se usan getters, setters y constructores explícitos de Lombok. Las clases no requieren `@Data` ni un `equals/hashCode` generado con todos los campos mutables.

### Reglas de consistencia

**Para cualquier solicitud:**

- `applicationReference` es única; la restricción SQL también protege frente a inserciones concurrentes de la misma referencia.
- `requestedCustomerId` conserva el identificador recibido.
- Si `customerId` no es nulo, el cliente debe existir y el valor debe coincidir con `requestedCustomerId`.
- `amount` y `termMonths` nunca pueden ser nulos en la fila persistida.
- `NUMERIC` permite valores especiales en PostgreSQL, pero las restricciones rechazan `NaN` e infinitos. `BigDecimal` tampoco representa esos valores.

**Para `APPROVED`:**

- `customerId` es obligatorio.
- `amount > 0`.
- `termMonths` está entre `6` y `60`, inclusive.
- `reasonCode` y `reason` son nulos.

**Para `REJECTED`:**

- `reasonCode` es obligatorio y pertenece al enum definido.
- `reason` es obligatorio y cumple `btrim(reason) <> ''`.
- Si `reasonCode == CUSTOMER_NOT_FOUND`, `customerId` debe ser nulo.
- El SQL permite guardar importes cero o negativos y plazos fuera del rango de aprobación para registrar solicitudes inválidas.
- Con los otros motivos de rechazo, el SQL permite tanto un `customerId` válido como un valor nulo; no impone que siempre exista un cliente asociado.

Por eso no conviene aplicar indiscriminadamente `@Positive` a `amount` ni `@Min(6)`/`@Max(60)` a `termMonths` en esta entidad de persistencia. La validación depende del resultado de la solicitud. Tampoco se debe permitir que una validación previa impida almacenar el rechazo que el modelo admite.

Las restricciones actuales no verifican que un cliente aprobado esté `ELIGIBLE`, ni comparan `amount` con `approvalLimit`. Esas comprobaciones corresponden al servicio de negocio. El SQL tampoco establece un orden de prioridad entre los motivos de rechazo ni una regla de consumo del límite.

## 7. Repositorios acordes con los permisos

Los siguientes ejemplos extienden `Repository` y exponen únicamente las operaciones mostradas. Las consultas devuelven `Mono` o `Flux`; no necesitan llamadas a `block()`.

### Consulta de clientes

```java
package com.example.creditos.persistence.repository;

import com.example.creditos.persistence.entity.CustomerEntity;
import org.springframework.data.repository.Repository;
import reactor.core.publisher.Mono;

public interface CustomerRepository extends Repository<CustomerEntity, String> {

    Mono<CustomerEntity> findById(String customerId);
}
```

Si el flujo de negocio utiliza `SELECT ... FOR UPDATE`, ejecuta el bloqueo y las operaciones dependientes en la misma transacción reactiva, por ejemplo mediante `TransactionalOperator`. El bloqueo termina al finalizar la transacción.

### Consulta e inserción de solicitudes

Una inserción explícita permite omitir `id` y `processed_at`, dejando ambos valores a PostgreSQL. `RETURNING *` devuelve la fila con su identificador y fecha definitivos.

```java
package com.example.creditos.persistence.repository;

import java.math.BigDecimal;

import com.example.creditos.persistence.entity.CreditApplicationEntity;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

public interface CreditApplicationRepository
        extends Repository<CreditApplicationEntity, Long> {

    Mono<CreditApplicationEntity> findById(Long id);

    Mono<CreditApplicationEntity> findByApplicationReference(String applicationReference);

    Flux<CreditApplicationEntity> findByCustomerIdOrderByProcessedAtDescIdDesc(String customerId);

    @Query("""
        INSERT INTO public.credit_applications (
            application_reference,
            requested_customer_id,
            customer_id,
            amount,
            term_months,
            status,
            reason_code,
            reason
        ) VALUES (
            :applicationReference,
            :requestedCustomerId,
            :customerId,
            :amount,
            :termMonths,
            :status,
            :reasonCode,
            :reason
        )
        RETURNING *
        """)
    Mono<CreditApplicationEntity> insert(
        @Param("applicationReference") String applicationReference,
        @Param("requestedCustomerId") String requestedCustomerId,
        @Param("customerId") String customerId,
        @Param("amount") BigDecimal amount,
        @Param("termMonths") Integer termMonths,
        @Param("status") String status,
        @Param("reasonCode") String reasonCode,
        @Param("reason") String reason
    );
}
```

Los bloques de texto de este ejemplo requieren Java 15 o posterior. Usa el JDK requerido por tu versión de Spring Boot.

En el método `insert`, `status` y `reasonCode` se reciben como `String` para enlazar directamente las columnas `TEXT`; al invocarlo desde el servicio pasa `status.name()` y, si existe, `reasonCode.name()`. La entidad devuelta utiliza los enums definidos anteriormente.

No añadas `@Modifying` a esta consulta: interesa mapear la fila producida por `RETURNING *`, en lugar de obtener un contador de filas modificadas.

Ejemplo de preparación e inserción de una solicitud aprobada, dentro de un servicio que dispone de `creditApplicationRepository`:

```java
CreditApplicationEntity application = CreditApplicationEntity.builder()
    .applicationReference("APP-0001")
    .requestedCustomerId("CLI-1001")
    .customerId("CLI-1001")
    .amount(new BigDecimal("2500000"))
    .termMonths(12)
    .status(CreditApplicationStatus.APPROVED)
    .build();

Mono<CreditApplicationEntity> persisted = creditApplicationRepository.insert(
    application.getApplicationReference(),
    application.getRequestedCustomerId(),
    application.getCustomerId(),
    application.getAmount(),
    application.getTermMonths(),
    application.getStatus().name(),
    application.getReasonCode() == null ? null : application.getReasonCode().name(),
    application.getReason()
);
```

Este fragmento muestra persistencia; las verificaciones de negocio deben realizarse antes, dentro del flujo reactivo. Devuelve o compón `persisted` en ese flujo para que la operación se ejecute cuando exista una suscripción.

### Identificador, fecha y escrituras posteriores

- No asignes manualmente `id`: la columna es `GENERATED ALWAYS AS IDENTITY`.
- Un `DEFAULT` SQL se aplica al omitir la columna o usar `DEFAULT`; insertar explícitamente `NULL` no activa ese valor por defecto. La consulta anterior omite `processed_at` de forma deliberada.
- PostgreSQL almacena el instante de `TIMESTAMPTZ`, no la zona original. El servidor está configurado en UTC; la representación puede normalizarse a UTC al serializar.
- Si eliges `ReactiveCrudRepository.save()` en otro diseño, usa una entidad nueva con `id == null` y define una estrategia explícita para `processedAt`: asignarlo antes de insertar o asegurar que se omita del insert para aprovechar el valor de la BD.
- Una entidad con `id` no nulo puede provocar un `UPDATE` al llamar a `save()`. El usuario actual no tiene permiso `UPDATE` sobre `credit_applications`; tampoco tiene permiso `DELETE`.
- La referencia única sirve para identificar una solicitud y evitar duplicados. Si dos inserciones compiten, trata la violación de unicidad de forma coherente con la política de idempotencia de tu servicio.

## 8. Relaciones, índices y alcance del modelo

- `CreditApplicationEntity.customerId` representa la relación; no añadas un campo persistente `CustomerEntity customer` esperando una carga automática.
- Para devolver solicitudes con datos del cliente, utiliza consultas separadas o un `LEFT JOIN` con un DTO/proyección. El `LEFT JOIN` conserva las solicitudes sin cliente asociado.
- La FK tiene `ON UPDATE RESTRICT` y `ON DELETE RESTRICT`: no hay actualización ni eliminación en cascada del cliente referenciado.
- El índice `idx_credit_applications_customer_status` cubre `(customer_id, status)`.
- El índice `idx_credit_applications_processed_at` cubre `(processed_at DESC, id DESC)` y permite un orden temporal con desempate por ID.
- La unicidad de `application_reference` ya tiene su índice asociado en PostgreSQL.
- Los índices y restricciones se administran mediante SQL, no mediante anotaciones Lombok o R2DBC.
- No hay columnas de versión, borrado lógico, fecha de creación adicional ni fecha de actualización. No añadas esos campos persistentes sin modificar antes el esquema.

## 9. Estructura sugerida

```text
src/main/java/com/example/creditos/
└── persistence/
    ├── entity/
    │   ├── CustomerEntity.java
    │   ├── CustomerStatus.java
    │   ├── CreditApplicationEntity.java
    │   ├── CreditApplicationStatus.java
    │   └── ReasonCode.java
    └── repository/
        ├── CustomerRepository.java
        └── CreditApplicationRepository.java
```

Los DTO de entrada/salida y las reglas de aprobación pueden vivir en capas propias. Las entidades anteriores reflejan directamente las dos tablas existentes y sus tipos de datos.
