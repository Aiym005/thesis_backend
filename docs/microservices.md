# Microservice Runbook

The repository now contains separate Spring Boot entry points for a RabbitMQ-based microservice topology.

## Services

- `com.tms.thesissystem.microservices.gateway.ApiGatewayApplication`
- `com.tms.thesissystem.microservices.user.UserServiceApplication`
- `com.tms.thesissystem.microservices.workflow.WorkflowServiceApplication`
- `com.tms.thesissystem.microservices.notification.NotificationServiceApplication`
- `com.tms.thesissystem.microservices.audit.AuditServiceApplication`

## Ports

- Gateway: `8080`
- User service: `8081`
- Workflow service: `8082`
- Notification service: `8083`
- Audit service: `8084`

## Messaging

- RabbitMQ exchange: `thesis.workflow.exchange`
- Notification queue: `thesis.workflow.notifications`
- Audit queue: `thesis.workflow.audit`

Workflow service publishes events to RabbitMQ. Notification and audit services subscribe to those events independently.

## Run

Open separate terminals and run:

```bash
./mvnw spring-boot:run -Dspring-boot.run.mainClass=com.tms.thesissystem.microservices.user.UserServiceApplication
./mvnw spring-boot:run -Dspring-boot.run.mainClass=com.tms.thesissystem.microservices.workflow.WorkflowServiceApplication
./mvnw spring-boot:run -Dspring-boot.run.mainClass=com.tms.thesissystem.microservices.notification.NotificationServiceApplication
./mvnw spring-boot:run -Dspring-boot.run.mainClass=com.tms.thesissystem.microservices.audit.AuditServiceApplication
./mvnw spring-boot:run -Dspring-boot.run.mainClass=com.tms.thesissystem.microservices.gateway.ApiGatewayApplication
```

Start RabbitMQ before the workflow, notification, and audit services.

## Docker Compose

The repository also includes a single-command container topology:

```bash
docker compose up --build
```

Services:

- API Gateway: `http://localhost:8080`
- User service: `http://localhost:8081`
- Workflow service: `http://localhost:8082`
- Notification service: `http://localhost:8083`
- Audit service: `http://localhost:8084`
- RabbitMQ management: `http://localhost:15672`

Compose provisions PostgreSQL with the schema expected by the current repository implementation from `docker/postgres-init/01-schema.sql`.
