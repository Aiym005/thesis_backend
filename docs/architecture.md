# Thesis System Architecture

The project is now organized around the architecture shown in the diagram, while still running as a single Spring Boot deployment for development simplicity.

## Gateway

- `GET /api/gateway/dashboard`
- `GET /api/gateway/dashboard-async`
- `GET /api/gateway/system-map`

The gateway is the entry point that exposes aggregated views and the service topology.

## User Service

- `GET /api/users`
- `POST /api/users/login`
- `POST /api/users/forgot-password`

This bounded context owns user directory and authentication-related API.

## Topic Service

- `GET /api/topics`
- `POST /api/topics/proposals/student`
- `POST /api/topics/proposals/teacher`
- `POST /api/topics/claim`
- `POST /api/topics/approvals/teacher`
- `POST /api/topics/approvals/department`

This bounded context owns topic proposal, selection, and approval workflow.

## Plan Service

- `GET /api/plans`
- `POST /api/plans`
- `POST /api/plans/submit`
- `POST /api/plans/approvals/teacher`
- `POST /api/plans/approvals/department`

This bounded context owns weekly plan creation and approval flow.

## Review Service

- `GET /api/reviews`
- `POST /api/reviews`

This bounded context owns review submission and listing.

## Notification Service

- `GET /api/notifications`

Notifications are populated from workflow events. The implementation still uses the existing event publisher and listener infrastructure.

## Audit Service

- `GET /api/audits`

Audit entries are projected from workflow events through the same event bus abstraction.

## Event Bus

The system supports two modes:

- Local in-process event publishing
- RabbitMQ-backed publish/subscribe when `app.messaging.rabbit.enabled=true`

That keeps the code aligned with an event-driven microservice topology even though the development deployment remains a single app.
