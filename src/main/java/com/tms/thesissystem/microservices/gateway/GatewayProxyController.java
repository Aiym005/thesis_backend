package com.tms.thesissystem.microservices.gateway;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.tms.thesissystem.api.ApiDtos;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequiredArgsConstructor
public class GatewayProxyController {
    private static final String SESSION_AUTH_USER = "auth.user";
    private static final String SESSION_AUTH_TOKEN = "auth.token";

    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ServiceEndpointProperties endpoints;

    @GetMapping("/api/gateway/system-map")
    public Map<String, Object> systemMap() {
        return Map.of(
                "gateway", Map.of("name", "api-gateway", "port", 8080),
                "services", List.of(
                        Map.of("name", "user-service", "baseUrl", endpoints.getUser().getBaseUrl()),
                        Map.of("name", "workflow-service", "baseUrl", endpoints.getWorkflow().getBaseUrl()),
                        Map.of("name", "notification-service", "baseUrl", endpoints.getNotification().getBaseUrl()),
                        Map.of("name", "audit-service", "baseUrl", endpoints.getAudit().getBaseUrl())
                ),
                "eventBus", "rabbitmq"
        );
    }

    @GetMapping("/api/dashboard")
    public ResponseEntity<String> dashboard(HttpServletRequest request) throws IOException, InterruptedException {
        String token = sessionToken(request);
        ApiDtos.AuthUserDto authUser = sessionUser(request);
        boolean studentSession = isStudent(authUser);
        Long studentUserId = studentSession ? authUser.id() : null;
        HttpResponse<String> workflowResponse = send(HttpMethod.GET, workflowUrl("/api/dashboard"), null, token);
        if (workflowResponse.statusCode() >= 400) {
            return jsonResponse(workflowResponse.statusCode(), workflowResponse.body());
        }

        ObjectNode dashboard = (ObjectNode) objectMapper.readTree(workflowResponse.body());
        ArrayNode workflowNotifications = readArrayFieldOrEmpty(dashboard, "notifications");
        ArrayNode workflowAuditTrail = readArrayFieldOrEmpty(dashboard, "auditTrail");
        if (studentSession) {
            workflowNotifications = filterNotificationsByUserId(workflowNotifications, studentUserId);
        }
        ArrayNode notifications = readArrayOrEmpty(notificationUrl(studentUserId), token);
        ArrayNode auditTrail = readArrayOrEmpty(auditUrl(), token);
        dashboard.set("notifications", notifications.isEmpty() ? workflowNotifications : notifications);
        dashboard.set("auditTrail", auditTrail.isEmpty() ? workflowAuditTrail : auditTrail);
        return jsonResponse(workflowResponse.statusCode(), objectMapper.writeValueAsString(dashboard));
    }

    @GetMapping("/api/verification/state")
    public ResponseEntity<String> verificationState() throws IOException, InterruptedException {
        return proxy(HttpMethod.GET, workflowUrl("/api/verification/state"), null);
    }

    @GetMapping("/api/verification/users")
    public ResponseEntity<String> verificationUsers() throws IOException, InterruptedException {
        return proxy(HttpMethod.GET, workflowUrl("/api/verification/users"), null);
    }

    @GetMapping("/api/system/database")
    public ResponseEntity<String> databaseStatus() throws IOException, InterruptedException {
        return proxy(HttpMethod.GET, workflowUrl("/api/system/database"), null);
    }

    @PostMapping("/api/verification/topics/student-proposals")
    public ResponseEntity<String> studentTopicProposal(@RequestBody String payload) throws IOException, InterruptedException {
        return proxy(HttpMethod.POST, workflowUrl("/api/verification/topics/student-proposals"), payload);
    }

    @PostMapping("/api/verification/topics/teacher-proposals")
    public ResponseEntity<String> teacherTopicProposal(@RequestBody String payload) throws IOException, InterruptedException {
        return proxy(HttpMethod.POST, workflowUrl("/api/verification/topics/teacher-proposals"), payload);
    }

    @PostMapping("/api/verification/topics/department-proposals")
    public ResponseEntity<String> departmentTopicProposal(@RequestBody String payload) throws IOException, InterruptedException {
        return proxy(HttpMethod.POST, workflowUrl("/api/verification/topics/department-proposals"), payload);
    }

    @PostMapping("/api/verification/topics/selections")
    public ResponseEntity<String> topicSelection(@RequestBody String payload) throws IOException, InterruptedException {
        return proxy(HttpMethod.POST, workflowUrl("/api/verification/topics/selections"), payload);
    }

    @PostMapping("/api/verification/topics/teacher-approvals")
    public ResponseEntity<String> teacherTopicApproval(@RequestBody String payload) throws IOException, InterruptedException {
        return proxy(HttpMethod.POST, workflowUrl("/api/verification/topics/teacher-approvals"), payload);
    }

    @PostMapping("/api/verification/topics/department-approvals")
    public ResponseEntity<String> departmentTopicApproval(@RequestBody String payload) throws IOException, InterruptedException {
        return proxy(HttpMethod.POST, workflowUrl("/api/verification/topics/department-approvals"), payload);
    }

    @PostMapping("/api/verification/topics/student-updates")
    public ResponseEntity<String> studentTopicUpdate(@RequestBody String payload) throws IOException, InterruptedException {
        return proxy(HttpMethod.POST, workflowUrl("/api/verification/topics/student-updates"), payload);
    }

    @PostMapping("/api/verification/topics/teacher-updates")
    public ResponseEntity<String> teacherTopicUpdate(@RequestBody String payload) throws IOException, InterruptedException {
        return proxy(HttpMethod.POST, workflowUrl("/api/verification/topics/teacher-updates"), payload);
    }

    @PostMapping("/api/verification/topics/department-updates")
    public ResponseEntity<String> departmentTopicUpdate(@RequestBody String payload) throws IOException, InterruptedException {
        return proxy(HttpMethod.POST, workflowUrl("/api/verification/topics/department-updates"), payload);
    }

    @PostMapping("/api/verification/topics/deletions")
    public ResponseEntity<String> topicDeletion(@RequestBody String payload) throws IOException, InterruptedException {
        return proxy(HttpMethod.POST, workflowUrl("/api/verification/topics/deletions"), payload);
    }

    @PostMapping("/api/topics/department-catalog")
    public ResponseEntity<String> departmentCatalogTopic(@RequestBody String payload) throws IOException, InterruptedException {
        return proxy(HttpMethod.POST, workflowUrl("/api/topics/department-catalog"), payload);
    }

    @PostMapping("/api/topics/student-update")
    public ResponseEntity<String> studentTopicUpdateLegacy(@RequestBody String payload) throws IOException, InterruptedException {
        return proxy(HttpMethod.POST, workflowUrl("/api/topics/student-update"), payload);
    }

    @PostMapping("/api/topics/teacher-update")
    public ResponseEntity<String> teacherTopicUpdateLegacy(@RequestBody String payload) throws IOException, InterruptedException {
        return proxy(HttpMethod.POST, workflowUrl("/api/topics/teacher-update"), payload);
    }

    @PostMapping("/api/topics/department-update")
    public ResponseEntity<String> departmentTopicUpdateLegacy(@RequestBody String payload) throws IOException, InterruptedException {
        return proxy(HttpMethod.POST, workflowUrl("/api/topics/department-update"), payload);
    }

    @PostMapping("/api/topics/delete")
    public ResponseEntity<String> topicDeletionLegacy(@RequestBody String payload) throws IOException, InterruptedException {
        return proxy(HttpMethod.POST, workflowUrl("/api/topics/delete"), payload);
    }

    @PostMapping("/api/verification/plans")
    public ResponseEntity<String> savePlan(@RequestBody String payload) throws IOException, InterruptedException {
        return proxy(HttpMethod.POST, workflowUrl("/api/verification/plans"), payload);
    }

    @PostMapping("/api/verification/plans/submit")
    public ResponseEntity<String> submitPlan(@RequestBody String payload) throws IOException, InterruptedException {
        return proxy(HttpMethod.POST, workflowUrl("/api/verification/plans/submit"), payload);
    }

    @PostMapping("/api/verification/plans/teacher-approvals")
    public ResponseEntity<String> teacherPlanApproval(@RequestBody String payload) throws IOException, InterruptedException {
        return proxy(HttpMethod.POST, workflowUrl("/api/verification/plans/teacher-approvals"), payload);
    }

    @PostMapping("/api/verification/plans/department-approvals")
    public ResponseEntity<String> departmentPlanApproval(@RequestBody String payload) throws IOException, InterruptedException {
        return proxy(HttpMethod.POST, workflowUrl("/api/verification/plans/department-approvals"), payload);
    }

    @PostMapping("/api/reviews")
    public ResponseEntity<String> submitReview(@RequestBody String payload) throws IOException, InterruptedException {
        return proxy(HttpMethod.POST, workflowUrl("/api/reviews"), payload);
    }

    @PostMapping("/api/auth/login")
    public ResponseEntity<String> login(@RequestBody String payload, HttpServletRequest request) throws IOException, InterruptedException {
        HttpResponse<String> response = send(userUrl("/api/users/login"), payload);
        if (response.statusCode() >= 400) {
            return jsonResponse(response.statusCode(), response.body());
        }

        ApiDtos.LoginResponse loginResponse = objectMapper.readValue(response.body(), ApiDtos.LoginResponse.class);
        if (loginResponse.user() != null) {
            HttpSession existingSession = request.getSession(false);
            if (existingSession != null) {
                existingSession.invalidate();
            }
            HttpSession session = request.getSession(true);
            session.setAttribute(SESSION_AUTH_USER, loginResponse.user());
            session.setAttribute(SESSION_AUTH_TOKEN, loginResponse.token());
        }
        return jsonResponse(response.statusCode(), response.body());
    }

    @PostMapping("/api/auth/register")
    public ResponseEntity<String> register(@RequestBody String payload, HttpServletRequest request) throws IOException, InterruptedException {
        HttpResponse<String> response = send(userUrl("/api/users/register"), payload);
        if (response.statusCode() >= 400) {
            return jsonResponse(response.statusCode(), response.body());
        }

        ApiDtos.RegistrationResponse registrationResponse = objectMapper.readValue(response.body(), ApiDtos.RegistrationResponse.class);
        if (registrationResponse.user() != null) {
            HttpSession existingSession = request.getSession(false);
            if (existingSession != null) {
                existingSession.invalidate();
            }
            HttpSession session = request.getSession(true);
            session.setAttribute(SESSION_AUTH_USER, registrationResponse.user());
            session.setAttribute(SESSION_AUTH_TOKEN, registrationResponse.token());
        }
        return jsonResponse(response.statusCode(), response.body());
    }

    @PostMapping("/api/auth/forgot-password")
    public ResponseEntity<String> forgotPassword(@RequestBody String payload) throws IOException, InterruptedException {
        return proxy(HttpMethod.POST, userUrl("/api/users/forgot-password"), payload);
    }

    @GetMapping("/api/auth/session")
    public ResponseEntity<String> session(HttpServletRequest request) throws IOException {
        HttpSession session = request.getSession(false);
        Object sessionUser = session == null ? null : session.getAttribute(SESSION_AUTH_USER);
        Object sessionToken = session == null ? null : session.getAttribute(SESSION_AUTH_TOKEN);
        if (!(sessionUser instanceof ApiDtos.AuthUserDto authUser)) {
            return jsonResponse(200, objectMapper.writeValueAsString(new ApiDtos.SessionResponse(false, null, null)));
        }
        return jsonResponse(200, objectMapper.writeValueAsString(new ApiDtos.SessionResponse(true, authUser, sessionToken instanceof String token ? token : null)));
    }

    @PostMapping("/api/auth/logout")
    public ResponseEntity<String> logout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .body("{\"ok\":true}");
    }

    @GetMapping("/api/users")
    public ResponseEntity<String> users(HttpServletRequest request) throws IOException, InterruptedException {
        HttpResponse<String> response = send(HttpMethod.GET, userUrl("/api/users"), null, sessionToken(request));
        return jsonResponse(response.statusCode(), response.body());
    }

    @GetMapping("/api/users/login-enabled-teachers")
    public ResponseEntity<String> loginEnabledTeachers(HttpServletRequest request) throws IOException, InterruptedException {
        HttpResponse<String> response = send(HttpMethod.GET, userUrl("/api/users/login-enabled-teachers"), null, sessionToken(request));
        return jsonResponse(response.statusCode(), response.body());
    }

    @GetMapping("/api/topics")
    public ResponseEntity<String> topics() throws IOException, InterruptedException {
        return proxy(HttpMethod.GET, workflowUrl("/api/topics"), null);
    }

    @GetMapping("/api/notifications")
    public ResponseEntity<String> notifications(HttpServletRequest request) throws IOException, InterruptedException {
        ApiDtos.AuthUserDto authUser = sessionUser(request);
        Long studentUserId = isStudent(authUser) ? authUser.id() : null;
        return proxy(HttpMethod.GET, notificationUrl(studentUserId), null);
    }

    @GetMapping("/api/audits")
    public ResponseEntity<String> audits() throws IOException, InterruptedException {
        return proxy(HttpMethod.GET, auditUrl(), null);
    }

    private String userUrl(String path) {
        return endpoints.getUser().getBaseUrl() + path;
    }

    private String workflowUrl(String path) {
        return endpoints.getWorkflow().getBaseUrl() + path;
    }

    private String notificationUrl(Long userId) {
        String base = endpoints.getNotification().getBaseUrl() + "/api/notifications";
        return userId == null ? base : base + "?userId=" + userId;
    }

    private String auditUrl() {
        return endpoints.getAudit().getBaseUrl() + "/api/audits";
    }

    private ResponseEntity<String> proxy(HttpMethod method, String url, String body) {
        try {
            HttpResponse<String> response = send(method, url, body, currentSessionToken());
            return jsonResponse(response.statusCode(), response.body());
        } catch (IOException exception) {
            log.error("Downstream call failed: method={}, url={}", method, url, exception);
            return jsonResponse(502, "{\"message\":\"Дотоод service-тэй холбогдож чадсангүй. Дахин оролдоно уу.\"}");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            log.error("Downstream call interrupted: method={}, url={}", method, url, exception);
            return jsonResponse(502, "{\"message\":\"Service хүсэлт дундаа тасалдлаа. Дахин оролдоно уу.\"}");
        }
    }

    private HttpResponse<String> send(String url, String body) throws IOException, InterruptedException {
        return send(HttpMethod.POST, url, body, null);
    }

    private HttpResponse<String> send(HttpMethod method, String url, String body, String bearerToken) throws IOException, InterruptedException {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
                .timeout(Duration.ofSeconds(10))
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        if (bearerToken != null && !bearerToken.isBlank()) {
            builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + bearerToken);
        }

        if (method == HttpMethod.POST) {
            builder.POST(HttpRequest.BodyPublishers.ofString(body == null ? "" : body));
        } else {
            builder.GET();
        }

        return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    private ArrayNode readArrayOrEmpty(String url, String bearerToken) {
        try {
            HttpResponse<String> response = send(HttpMethod.GET, url, null, bearerToken);
            if (response.statusCode() >= 400) {
                return objectMapper.createArrayNode();
            }

            JsonNode payload = objectMapper.readTree(response.body());
            return payload != null && payload.isArray() ? (ArrayNode) payload : objectMapper.createArrayNode();
        } catch (IOException | InterruptedException exception) {
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.warn("Failed to fetch downstream array from {}: {}", url, exception.getMessage());
            return objectMapper.createArrayNode();
        }
    }

    private ArrayNode readArrayFieldOrEmpty(ObjectNode payload, String fieldName) {
        JsonNode node = payload.get(fieldName);
        return node != null && node.isArray() ? (ArrayNode) node : objectMapper.createArrayNode();
    }

    private ResponseEntity<String> jsonResponse(int statusCode, String body) {
        return ResponseEntity.status(statusCode)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body);
    }

    private String currentSessionToken() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attributes == null ? null : sessionToken(attributes.getRequest());
    }

    private String sessionToken(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        Object token = session == null ? null : session.getAttribute(SESSION_AUTH_TOKEN);
        return token instanceof String value && !value.isBlank() ? value : null;
    }

    private ApiDtos.AuthUserDto sessionUser(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        Object user = session == null ? null : session.getAttribute(SESSION_AUTH_USER);
        return user instanceof ApiDtos.AuthUserDto authUser ? authUser : null;
    }

    private boolean isStudent(ApiDtos.AuthUserDto user) {
        return user != null && "student".equalsIgnoreCase(user.role());
    }

    private ArrayNode filterNotificationsByUserId(ArrayNode notifications, Long userId) {
        if (userId == null) {
            return notifications;
        }
        ArrayNode filtered = objectMapper.createArrayNode();
        notifications.forEach(item -> {
            JsonNode node = item.get("userId");
            if (node != null && node.canConvertToLong() && userId.equals(node.longValue())) {
                filtered.add(item);
            }
        });
        return filtered;
    }
}
