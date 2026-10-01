# back-IAS

## Local database connection

The backend uses Spring Data R2DBC and the application database user created by
the PostgreSQL scripts in `../compose-sistema-IAS`.

### Start PostgreSQL

From `C:\Users\Usuario\Documents\compose-sistema-IAS`, run:

```powershell
docker compose up -d postgres
```

### Start the backend

From the backend project directory, run:

```powershell
.\gradlew.bat bootRun
```

For the current development stage, `application.properties` contains the local
connection URL and application-user credentials directly. The database is
`DATABAE_IAS`, exposed by Compose at `localhost:5432`. This temporary configuration
will be replaced with environment variables later.

In IntelliJ, run `DemoApplication` with the default profile and use the backend
project directory as the working directory.

If the backend is later moved into the PostgreSQL Compose network, change the
connection host to `postgres` and use the internal port `5432`.

The backend does not initialize the schema: `spring.sql.init.mode=never`.
PostgreSQL initialization is managed by the existing Compose scripts.

## Credit application API

Frontend integration contract: [API_FRONTEND.md](API_FRONTEND.md), with endpoint
examples, response schemas, business statuses, retry behavior and error handling.

| Method | Route | Behavior |
|---|---|---|
| POST | `/applications` | Process a new application or return its original result. |
| GET | `/applications/{reference}` | Retrieve a processed application. |
| GET | `/applications?limit=20` | List recent applications; limit must be between 1 and 100. |

The request contains `applicationReference`, `customerId`, `amount` and
`termMonths`. The amount is decoded directly as `BigDecimal` and returned as a
decimal string. Missing fields produce HTTP 400. Nonpositive amounts and terms
outside 6–60 months produce stored credit rejections for known customers.

Approvals also require an eligible customer and sufficient cumulative credit
limit. Unknown customers produce a stored `CUSTOMER_NOT_FOUND` rejection.

New applications return HTTP 201, including rejections. Identical retries return
HTTP 200 without reevaluation; changed customer, amount or term under an existing
reference produces HTTP 409. Amounts are compared numerically, regardless of scale.

New decisions are inserted inside a transaction after locking the customer and
reading the approved total. Results are delivered after commit. Duplicate
references are recovered outside the rolled-back transaction.

Errors return `code`, `message` and `traceId`. Public messages are in Spanish, and
`X-Trace-Id` contains the same identifier. Application lookup failures return 404;
technical failures return 500 with a generic public message.

## Swagger UI and OpenAPI

After starting the backend, open:

- Swagger UI: `http://localhost:8080/swagger-ui.html`
- OpenAPI JSON: `http://localhost:8080/v3/api-docs`
- OpenAPI YAML: `http://localhost:8080/v3/api-docs.yaml`

The three functional routes are documented directly in `ApplicationRouter` with
`@RouterOperations`. Swagger UI includes request examples, response schemas,
the recent-list limit and HTTP error responses. API descriptions are in Spanish.
`OpenApiConfiguration` defines the API title, version and tag.

Expand an operation, select **Try it out**, edit the request or parameters, and
select **Execute**. A POST uses the real processing flow and persists the decision.
Use a new `applicationReference` for a new credit request; repeat the same
reference and data to retrieve its original result without consuming more credit.

Swagger UI, its configuration and the generated OpenAPI document were verified
through HTTP on a temporary backend instance. No credit requests were created
during that verification.

## Unit tests

Run the domain, application, R2DBC mapper, adapter and exception unit tests with:

```powershell
.\gradlew.bat test --tests "com.backend_IAS.demo.domain.*" --tests "com.backend_IAS.demo.application.*" --tests "com.backend_IAS.demo.infrastructure.r2dbc.mapper.*" --tests "com.backend_IAS.demo.infrastructure.r2dbc.adapter.*" --tests "com.backend_IAS.demo.exception.*"
```

Mapper tests cover DTO/domain conversions, exact decimal values, optional customer
links, historical rejection descriptions, processing timestamps, retry messages
and persistence error translation. Adapter tests cover deferred repository calls,
empty results, database-generated responses, limit forwarding and error handling.
Transaction adapter tests use the real `TransactionalOperator` with a mocked
transaction manager to verify commit-before-result, rollback, commit failures,
deferred operation creation and cancellation. These unit tests do not require a
database or a Spring application context. Exception tests verify literal identifier
formatting, validation details, stable messages and preservation of original and
nested causes. The latest run passed 113 tests, including 47 mapper tests,
34 adapter tests and 16 exception tests.
The HTML report is available at `build/reports/tests/test/index.html`.

## Automated flow and integration tests

With Docker running, execute the complete suite:

```powershell
.\gradlew.bat test
```

To run only the HTTP processing scenarios:

```powershell
.\gradlew.bat test --tests "com.backend_IAS.demo.integration.ApplicationFlowsIntegrationTest"
```

`ApplicationFlowsIntegrationTest` starts the backend on a random port and uses a
dedicated PostgreSQL 17 Testcontainers database. HTTP requests go through the real
router, handler, use cases, rules, repositories and transaction manager. The
backend connects with a restricted test application user; a separate admin
connection resets the fixture before each scenario and injects controlled failures.

The 47 flow tests verify approvals, accumulated credit usage, exact credit-limit
boundaries, stored rejection reasons and precedence, decimal precision, numeric
idempotency, historical retries after customer changes, conflicting references,
request validation, query results and ordering, correlated error responses,
unsupported content types, insertion failures and deferred commit failures.
Concurrent HTTP scenarios verify credit-limit protection and duplicate-reference
recovery after rollback, including unknown customers and cross-customer conflicts.
In duplicate-reference scenarios, a spy synchronizes the initial reads to make the
race deterministic; database operations remain real.

The complete suite passed 174 tests: 113 unit tests, 47 HTTP flow tests, 13 existing
persistence integration tests and one Spring context test. No tests failed or were
skipped. The integration fixtures run in isolated containers.
