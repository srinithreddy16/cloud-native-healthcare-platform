## Patient Management Microservices

> A microservices-based patient management system with secure API gateway, JWT authentication, event-driven analytics/notifications, and infrastructure-as-code for LocalStack.

---

### Overview

This repository contains a **patient management system** built as a set of Spring Boot microservices, fronted by an API Gateway and deployed locally to **LocalStack** (AWS emulator) using the **AWS CDK (Java)**.

#### Microservices

- **Auth Service** (`auth-service`): Issues and validates JWTs for user authentication, backed by PostgreSQL.
- **Patient Service** (`patient-service`): Manages patient records (CRUD), publishes patient events to Kafka, and calls Billing Service via gRPC.
- **Billing Service** (`billing-service`): Manages billing and payments and exposes a gRPC endpoint.
- **Analytics Service** (`analytics-service`): Consumes patient events from Kafka to compute analytics/insights.
- **Notification Service** (`notification-service`): Consumes events from Kafka and triggers notifications.
- **API Gateway** (`api-gateway`): Spring Cloud Gateway (WebFlux) that routes external traffic, validates JWTs, and exposes aggregated OpenAPI endpoints (`/api-docs/*`).

#### Supporting Modules

- **Infrastructure** (`infrastructure`):
  - AWS CDK (Java) stack `LocalStack` that defines:
    - VPC and networking.
    - Two PostgreSQL RDS instances for Auth and Patient services.
    - MSK Kafka cluster.
    - Route53 health checks for the DBs.
    - ECS Fargate cluster and services for all microservices and the API Gateway.
    - Application Load Balancer for the gateway.
  - Deployable to **LocalStack** via `localstack-deploy.sh`.
- **Integration Tests** (`integration-tests`):
  - REST Assured + JUnit 5 integration tests for the Auth and Patient flows.
- **API Requests** (`api-requests`):
  - IDE-friendly `.http` files for manual testing of Auth and Patient endpoints via the gateway.

---

### Architecture

#### Tech Stack

- **Language & Frameworks**:
  - Java 21, Spring Boot 4.x
  - Spring Web (MVC/WebFlux), Spring Data JPA, Spring Security
  - Spring Cloud Gateway
- **Messaging & RPC**:
  - Apache Kafka (MSK in AWS / LocalStack)
  - gRPC for Patient ↔ Billing communication
- **Databases**:
  - PostgreSQL (one DB per service: Auth, Patient)
- **Infra & Deployment**:
  - AWS CDK v2 (`aws-cdk-lib`) in Java
  - AWS Java SDK
  - LocalStack Pro for AWS resource emulation
  - Docker / Docker Desktop
- **Testing**:
  - JUnit 5 (5.11.4)
  - REST Assured (5.3.0)

#### High-Level Flow

1. **Authentication**:
   - Client calls `POST /auth/login` on the API Gateway.
   - API Gateway forwards to Auth Service.
   - Auth Service validates credentials against its PostgreSQL DB and issues a JWT.
2. **Protected APIs**:
   - Client sends `Authorization: Bearer <token>` to `GET /api/patients` and other protected routes.
   - `JwtValidationGatewayFilterFactory`:
     - Checks presence and `Bearer` format.
     - Calls `GET /validate` on Auth Service via `WebClient`.
     - On success, forwards the request to Patient Service.
3. **Patient / Billing**:
   - Patient Service persists patient data to its DB.
   - For certain operations, Patient Service calls Billing Service via gRPC.
4. **Events & Analytics**:
   - Patient Service publishes events to Kafka.
   - Analytics and Notification services consume events and react accordingly.
5. **Infrastructure**:
   - All services run as ECS Fargate tasks in a CDK-defined VPC.
   - RDS and MSK Kafka are created as part of the same stack.
   - API Gateway runs behind an Application Load Balancer.

---

### Project Structure

- **`api-gateway/`**
  - Spring Cloud Gateway app.
  - `GatewayConfig` – route definitions using `auth.service.url` and `patient.service.url`.
  - `JwtValidationGatewayFilterFactory` – validates JWTs by calling Auth Service.
  - `JwtValidationException` – global handler mapping unauthorized responses to `401`.
  - `application.yml` / `application-prod.yml` – gateway and route configuration.
- **`auth-service/`**
  - JWT issuing and user login.
  - `JwtUtil` – token generation and validation.
  - `AuthService`, `AuthController`, `SecurityConfig`.
  - PostgreSQL DB via Spring Data JPA.
- **`patient-service/`**
  - REST API for patient entities.
  - Kafka producer for patient events.
  - gRPC client for Billing Service.
- **`billing-service/`**
  - gRPC server handling billing operations.
- **`analytics-service/`**
  - Kafka consumer for analytics events.
- **`notification-service/`**
  - Kafka consumer for notification events.
- **`infrastructure/`**
  - `pom.xml` – CDK + AWS SDK dependencies.
  - `src/main/java/com/pm/stack/LocalStack.java` – CDK stack definition.
  - `localstack-deploy.sh` – deploy script for LocalStack.
  - `cdk.out/` – synthesized CloudFormation template (`localstack.template.json`).
- **`integration-tests/`**
  - `pom.xml` – REST Assured + JUnit.
  - `AuthIntegrationTests` – verifies `/auth/login`.
  - `PatientIntegrationTest` – verifies `/api/patients` via gateway using a real JWT.
- **`api-requests/`**
  - `.http` API request files (IDE-friendly):
    - `auth-service/login.http`, `auth-service/validate.http`
    - `patient-service/*.http` (create/get/update/delete)

---

### Running Locally (Without LocalStack)

You can run the services directly on your machine, which is useful for quick development and debugging.

#### Prerequisites

- Java 21
- Maven 3.9+
- Docker & Docker Desktop (optional but recommended for Postgres and Kafka)
- A running PostgreSQL instance for:
  - Auth Service DB
  - Patient Service DB
- A Kafka broker

#### Build All Services

From the repository root:

```bash
mvn clean package -DskipTests
```

#### Run Services

You can run each service from its module directory, for example:

```bash
cd auth-service
mvn spring-boot:run

cd patient-service
mvn spring-boot:run

cd billing-service
mvn spring-boot:run

cd analytics-service
mvn spring-boot:run

cd api-gateway
mvn spring-boot:run
```

Make sure ports match the expected configuration:

- **Auth Service**: `http://localhost:4005`
- **Patient Service**: `http://localhost:4000`
- **Billing Service**: `http://localhost:4001` (+ gRPC on `9001`)
- **Analytics Service**: `http://localhost:4002`
- **API Gateway**: `http://localhost:4004`

#### Test via HTTP Files

Use the `.http` files under `api-requests/` from your IDE’s HTTP client:

- **Login**: `api-requests/auth-service/login.http` – get a JWT and store it as `{{token}}`.
- **Validate**: `api-requests/auth-service/validate.http` – validate the JWT.
- **Patients via Gateway**: `api-requests/patient-service/get-patients.http` – call via gateway with `Authorization: Bearer {{token}}`.

---

### LocalStack Deployment (Infrastructure-as-Code)

The recommended way to run the **full system** (ECS, RDS, MSK, ALB) locally is via LocalStack and the CDK-defined `LocalStack` stack.

#### Prerequisites

- Docker Desktop
- LocalStack Pro (`localstack/localstack-pro` image)
- AWS CLI configured (any credentials; LocalStack does not use real AWS)
- Node.js + `npx` or the Java CDK entrypoint (depending on how you synthesize)
- JDK 21 + Maven

#### 1. Start LocalStack

Example `docker run` (adjust services and environment as needed):

```bash
docker run --rm -it \
  -p 4566:4566 -p 4510-4560:4510-4560 \
  -e SERVICES=cloudformation,ec2,ecs,elasticloadbalancingv2,rds,kafka,route53,logs \
  --name localstack-main \
  localstack/localstack-pro
```

Verify LocalStack is up:

```bash
curl http://localhost:4566/_localstack/health
```

#### 2. Build Docker Images

From the repository root:

```bash
docker build -t auth-service      ./auth-service
docker build -t patient-service   ./patient-service
docker build -t billing-service   ./billing-service
docker build -t analytics-service ./analytics-service
docker build -t api-gateway       ./api-gateway
```

LocalStack’s ECS integration uses these local images by name.

#### 3. Synthesize CDK Template

From `infrastructure/`:

```bash
cd infrastructure
mvn compile
# Ensure that src/main/java/com/pm/stack/LocalStack.java has been executed/synthesized
# so that ./cdk.out/localstack.template.json is up to date.
```

You should have:

- `infrastructure/cdk.out/localstack.template.json`

#### 4. Deploy to LocalStack

From `infrastructure/`:

```bash
./localstack-deploy.sh
```

Script contents:

```bash
#!/bin/bash
set -e # Stops the script if any command fails

aws --endpoint-url=http://localhost:4566 cloudformation delete-stack \
    --stack-name patient-management || true

aws --endpoint-url=http://localhost:4566 cloudformation deploy \
    --stack-name patient-management \
    --template-file "./cdk.out/localstack.template.json"

aws --endpoint-url=http://localhost:4566 elbv2 describe-load-balancers \
    --query "LoadBalancers[0].DNSName" --output text
```

On success you’ll see a load balancer DNS name like:

```text
lb-3f8ebc07.elb.localhost.localstack.cloud
```

#### 5. Verify Containers

Check running containers:

```bash
docker ps
```

You should see:

- `localstack-main` – LocalStack
- ECS task containers:
  - `api-gateway` on port `4004`
  - `auth-service` on `4005`
  - `patient-service` on `4000`
  - `billing-service` on `4001`, `9001`
  - `analytics-service` on `4002`

RDS and MSK Kafka are emulated **inside LocalStack**, so they do not appear as separate containers.

#### 6. Call Endpoints via Load Balancer

Use the ALB DNS name (from `localstack-deploy.sh`) combined with gateway port `4004`:

- **Login via gateway**:

```http
POST http://<lb-dns>:4004/auth/login
Content-Type: application/json

{
  "email": "testuser@test.com",
  "password": "password123"
}
```

- **Patients via gateway (protected)**:

```http
GET http://<lb-dns>:4004/api/patients
Authorization: Bearer <JWT>
```

---

### Security & JWT Details

- Auth Service signs JWTs with a secret provided via `JWT_SECRET`:
  - In LocalStack ECS, this comes from the Fargate service environment in `LocalStack`:
    - `JWT_SECRET=ghjoxYGVvlbvbnNHJHNJjJJjjLvnNG`
- The API Gateway validates JWTs by calling `GET /validate` on Auth Service, via `JwtValidationGatewayFilterFactory`:
  - Reads `Authorization` header.
  - If missing or not `Bearer`, returns `401`.
  - Uses `WebClient` with `auth.service.url` to call `/validate`.
- `JwtValidationException` maps `WebClientResponseException.Unauthorized` to a `401` response at the gateway.

---

### Integration Tests

Module: `integration-tests/`

- **Dependencies**:
  - `rest-assured:5.3.0`
  - `junit-jupiter:5.11.4`

- **Tests**:
  - `AuthIntegrationTests`:
    - `shouldReturnOKWithValidToken`:
      - Calls `POST http://localhost:4004/auth/login` (via gateway).
      - Asserts `200` and non-null `token`.
    - `shouldReturnUnauthorizedOnInvalidLogin`:
      - Uses invalid credentials, expects `401`.
  - `PatientIntegrationTest`:
    - Logs in to get a token.
    - Calls `GET http://localhost:4004/api/patients` with `Authorization: Bearer <token>` and asserts the root array is non-null.

Run tests from `integration-tests/`:

```bash
cd integration-tests
mvn test
```

---

### Configuration Highlights

#### API Gateway (`api-gateway`)

- `GatewayConfig`:
  - Uses injected URLs:
    - `auth.service.url` (default: `http://host.docker.internal:4005`)
    - `patient.service.url` (default: `http://host.docker.internal:4000`)
  - Routes:
    - `/auth/**` → Auth Service.
    - `/api/patients/**` → Patient Service (protected by JWT filter).
    - `/api-docs/patients` → Patient Service OpenAPI docs.
- `JwtValidationGatewayFilterFactory`:
  - Configured with `auth.service.url`.
  - Uses `WebClient` to invoke `/validate`.
  - On 4xx from Auth Service, returns `401`.

#### Infrastructure (`LocalStack` CDK Stack)

In `LocalStack.java`:

- **VPC**: `PatientManagementVPC`
- **RDS**:
  - `auth-service-db` (`AuthServiceDB`)
  - `patient-service-db` (`PatientServiceDB`)
  - Postgres 17.2, small instance type, `admin_user` with generated secret, `RemovalPolicy.DESTROY`.
- **Route53 Health Checks**:
  - `AuthServiceDBHealthCheck`, `PatientServiceDBHealthCheck` (TCP on DB endpoints).
- **MSK Cluster**:
  - Name: `kafka-cluster`
  - Kafka 2.8.0
  - `NumberOfBrokerNodes = 2` (multiple of the number of AZs).
- **ECS Cluster**:
  - `PatientManagementCluster`
  - Cloud Map namespace `patient-management.local`.
- **Fargate Services**:
  - `auth-service`, `patient-service`, `billing-service`, `analytics-service`.
  - Common helper `createFargateService(...)` sets:
    - Ports and CloudWatch logging.
    - Kafka bootstrap env (`SPRING_KAFKA_BOOTSTRAP_SERVERS`).
    - DB env vars for services with RDS (URL, username, password, JPA/Hikari settings).
- **API Gateway Fargate**:
  - `createApiGatewayService()`:
    - Uses `api-gateway` image.
    - Env:
      - `SPRING_PROFILES_ACTIVE=prod`
      - `AUTH_SERVICE_URL=http://host.docker.internal:4005`
    - Exposes port `4004`.
    - Fronted by `ApplicationLoadBalancedFargateService`.

---

### Future Enhancements

- Add more detailed observability (structured logging, tracing, metrics).
- Add a `docker-compose.yml` for non-LocalStack local development (Postgres + Kafka + services).
- Expand integration tests to cover:
  - gRPC billing flows.
  - Kafka-based event propagation (patient create → analytics/notifications).
- Parameterize environment-specific configuration (`dev`, `localstack`, `prod`) with Spring profiles and CDK context.

