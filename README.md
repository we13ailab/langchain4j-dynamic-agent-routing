# Dynamic Agent Routing — LangChain4j Supervisor vs Sequential Workflow Demo

<img width="1672" height="941" alt="LangChain4j Multi-Agent Workflow" src="https://github.com/user-attachments/assets/bb4871b2-91e0-4ceb-9427-67db41f64058" />

A Spring Boot application that demonstrates **dynamic agent routing** with LangChain4j. Instead of hard-coding the order in which specialised agents run, a **`SupervisorAgent`** — an LLM planner — reads each incoming customer request and decides at runtime which sub-agents (`OrderAgent`, `ShippingAgent`, `RefundAgent`, `ResponseAgent`) to invoke, based on their `@Agent` descriptions.

The project also exposes a **static sequential workflow** (`OrderAgent` → `ShippingAgent` → `RefundAgent` → `ResponseAgent`) over a separate endpoint, so you can compare the two composition styles side by side on the same set of agents, tools and data:

| | Static workflow (`/workflow`) | Supervisor routing (`/supervisor`) |
|---|---|---|
| Who decides the agent order | The developer, at compile time | The supervisor LLM, per request |
| Agents invoked per request | Always all four | Only the ones the request needs |
| Latency / token cost | Predictable, higher floor | Varies, cheaper for narrow requests |
| Best for | Fixed, auditable processes | Open-ended, mixed-topic conversations |

All agents can call structured Java tools backed by a MySQL database instead of making up answers, and every tool failure surfaces to the LLM only as a curated, user-safe message.

## Project purpose

The application shows how to:

- route requests dynamically between specialised agents with `AgenticServices.supervisorBuilder()`
- contrast that with a fixed `sequenceBuilder()` pipeline over the same agents
- use `@Agent(description = ...)` metadata as the routing signal the supervisor LLM reads
- share data between agents through the agentic scope (`@V` template variables and output keys)
- attach different tools to different agents in the same composition
- observe every agent invocation with an `AgentListener`
- handle tool errors safely with curated, LLM-visible messages
- integrate LangChain4j `@AiService` beans and Agentic agents in one Spring Boot app

## Technologies used

- Java 21
- Spring Boot 4.1.1
- Spring Web MVC
- Spring Data JPA
- MySQL Connector/J
- Spring Boot Docker Compose integration
- LangChain4j 1.21.0-beta31
- LangChain4j OpenAI Spring Boot starter
- LangChain4j Agentic API (`langchain4j-agentic`)
- GLM-5.3-Flash model via the OpenCode Go API (OpenAI-compatible endpoint)
- Maven build tool
- JUnit 5 for test setup

## Architecture overview

The system is organized around the following layers:

1. Controller layer
   - `AiSupportController`
   - Exposes endpoints under `/api/ai-support`

2. Agent layer (LangChain4j Agentic API)
   - `OrderAgent` — extracts the order ID and fetches real order details
   - `ShippingAgent` — fetches carrier, tracking number and delivery estimates
   - `RefundAgent` — checks refund eligibility against the refund policy
   - `ResponseAgent` — composes the final customer-facing answer
   - `WorkflowSupportAgent` — the static sequential composition of the four agents above
   - `SupervisorAgent` — the dynamic composition: an LLM planner that routes each request to the sub-agents it actually needs
   - Assembled programmatically in `AgentConfig` via `AgenticServices`

3. AI service layer (`@AiService` beans)
   - `JavaTechieAIAgent` — general-purpose chat agent
   - `OrderSupportAIAgent` — single-agent order support with tools

4. Domain/service layer
   - `OrderService` — business rules for orders
   - `RefundService` — refund policy lookup
   - `ShippingService` — shipment tracking lookup

5. Tool layer
   - `OrderTools` — `getOrderStatus`, `cancelOrder`
   - `RefundTool` — `checkRefundEligibility`
   - `ShippingTools` — `trackShipment`

6. Observability
   - `DebugAgentListener` — logs every agent start/end and the agentic scope (attached to both the sequence and the supervisor)

7. Persistence layer
   - `OrderRepository`, `RefundPolicyRepository`, `ShipmentTrackingRepository`

8. Database model
   - `OrderEntity` (table `orders`), `RefundPolicy` (table `refund_policy`), `ShipmentTracking` (table `shipment_tracking`)

## Key application flows

### Static workflow flow (`GET /api/ai-support/workflow`)

- the controller receives the customer request
- the agentic scope is created and the first agent is invoked
- `OrderAgent` extracts the order ID and calls the `getOrderStatus` tool; the result is written to the scope under the `orderDetails` output key
- `ShippingAgent` reads `{{orderDetails}}`, calls the `trackShipment` tool and writes `shippingDetails`
- `RefundAgent` reads `{{orderDetails}}`, determines the order status, calls the `checkRefundEligibility` tool and writes `refundDecision`
- `ResponseAgent` reads the request plus all three outputs and writes the final customer answer
- the workflow returns the `finalResponse` output to the controller

Every request runs this exact pipeline — a shipping-only question still executes the refund agent.

### Supervisor routing flow (`GET /api/ai-support/supervisor`)

- the controller receives the customer request
- the supervisor LLM is given the request plus each sub-agent's `@Agent` description:
  - `OrderAgent` — "Use this agent when the user wants order status, product information, delivery information or order details."
  - `ShippingAgent` — "Use this agent for shipment tracking, current location, carrier, delivery date and shipping delay questions."
  - `RefundAgent` — "Use this agent when the user asks whether an order is eligible for refund."
  - `ResponseAgent` — "Creates the final customer response using available order, shipping and refund information."
- the supervisor decides which agent(s) to invoke and in which order, re-planning after each response until the request is satisfied (bounded by `maxAgentsInvocations`, default 10)
- each invoked agent runs with its own tools and writes its output into the shared agentic scope
- with `SupervisorResponseStrategy.LAST`, the response of the last invoked sub-agent is returned to the controller

Example routing decisions:

| Request | Supervisor invokes | Skips |
|---|---|---|
| "What is the status of my order ORD-1001?" | `OrderAgent` → `ResponseAgent` | shipping, refund |
| "What is the tracking number for ORD-1003?" | `ShippingAgent` → `ResponseAgent` | refund |
| "Am I eligible for a refund on ORD-1001?" | `OrderAgent` → `RefundAgent` → `ResponseAgent` | shipping |
| "Where is ORD-1004 and can I still get a refund?" | order + shipping + refund + response | — |

Every step is logged by `DebugAgentListener` (`=== AGENT START ===` / `=== AGENT END ===`), and every tool call by SLF4J logging (`TOOL CALLED -> ...`).

## Project structure

```text
langchain4j-dynamic-agent-routing/
├── .gitignore
├── compose.yml
├── pom.xml
├── README.md
├── request.http
├── langchain4j-dynamic-agent-routing.postman_collection.json
├── src/
│   ├── main/
│   │   ├── java/com/javatechie/
│   │   │   ├── Langchain4jExampleApplication.java
│   │   │   ├── agents/
│   │   │   │   ├── OrderAgent.java
│   │   │   │   ├── RefundAgent.java
│   │   │   │   ├── ResponseAgent.java
│   │   │   │   ├── ShippingAgent.java
│   │   │   │   └── WorkflowSupportAgent.java
│   │   │   ├── config/
│   │   │   │   ├── AgentConfig.java
│   │   │   │   └── ToolErrorHandlingConfig.java
│   │   │   ├── controller/
│   │   │   │   └── AiSupportController.java
│   │   │   ├── exception/
│   │   │   │   ├── OrderCannotBeCancelledException.java
│   │   │   │   ├── OrderNotFoundException.java
│   │   │   │   ├── RefundPolicyNotFoundException.java
│   │   │   │   └── ShipmentTrackingNotFoundException.java
│   │   │   ├── listener/
│   │   │   │   └── DebugAgentListener.java
│   │   │   ├── model/
│   │   │   │   ├── OrderEntity.java
│   │   │   │   ├── RefundPolicy.java
│   │   │   │   └── ShipmentTracking.java
│   │   │   ├── repository/
│   │   │   │   ├── OrderRepository.java
│   │   │   │   ├── RefundPolicyRepository.java
│   │   │   │   └── ShipmentTrackingRepository.java
│   │   │   ├── service/
│   │   │   │   ├── JavaTechieAIAgent.java
│   │   │   │   ├── OrderService.java
│   │   │   │   ├── OrderSupportAIAgent.java
│   │   │   │   ├── RefundService.java
│   │   │   │   └── ShippingService.java
│   │   │   └── tool/
│   │   │       ├── OrderTools.java
│   │   │       ├── RefundTool.java
│   │   │       └── ShippingTools.java
│   │   └── resources/
│   │       ├── application.yaml
│   │       └── data.sql
│   └── test/
│       └── java/com/javatechie/
│           └── Langchain4JExampleApplicationTests.java
```

The root `.gitignore` excludes generated Maven output (`target/`), IDE project metadata, logs, temporary files, and common operating-system files.

## Configuration

The project is configured in `src/main/resources/application.yaml`.

### Main settings

```yaml
spring:
  application:
    name: langchain4j-example
  datasource:
    driver-class-name: com.mysql.cj.jdbc.Driver
    url: jdbc:mysql://localhost:3306/javatechie
    username: ${DB_USERNAME:root}
    password: ${DB_PASSWORD:Password}

  jpa:
    show-sql: true
    hibernate:
      ddl-auto: update
      naming:
        physical-strategy: org.hibernate.boot.model.naming.PhysicalNamingStrategyStandardImpl
    properties:
      hibernate:
        dialect: org.hibernate.dialect.MySQLDialect
    defer-datasource-initialization: true

  sql:
    init:
      mode: always

langchain4j:
  open-ai:
    chat-model:
      base-url: https://opencode.ai/inference/go/openai/v1
      api-key: ${OPENCODE_API_KEY}
      model-name: glm-5.3-flash
      custom-headers:
        User-Agent: langchain4j-dynamic-agent-routing/1.0
        x-opencode-session: langchain4j-dynamic-agent-routing-supervisor-workflow
      log-requests: true
      log-responses: true
```

### Important notes

- The project expects a MySQL database named `javatechie` on localhost.
- The app uses `ddl-auto: update`, so Hibernate can create or update tables based on the entity model.
- The LLM is served through the OpenAI-compatible OpenCode Go endpoint (`base-url` above). Set the `OPENCODE_API_KEY` environment variable before starting the application; the key is never stored in source control. Other Chat-Completions-native Go models (for example `glm-5.3`, `kimi-k3.1-pro`, `deepseek-v4.1-flash`) can be used by changing `model-name`.
- The custom `User-Agent` and `x-opencode-session` headers identify the client to the Go endpoint and enable routing and prompt-caching optimizations.
- A commented-out plain-OpenAI fallback block is included in `application.yaml` — uncomment it and set `OPENAI_API_KEY` to switch back.
- Database credentials default to local development values and can be overridden with the `DB_USERNAME` and `DB_PASSWORD` environment variables.
- The server listens on the Spring Boot default port `8080` (no `server.port` override).

## Data model

The application uses three JPA entities.

### `OrderEntity` (table `orders`)

Entity fields:

- `id` — primary key
- `orderId` — unique order identifier (`order_id` column)
- `customerName`
- `productName`
- `status`
- `currentLocation`
- `expectedDelivery`
- `totalAmount`
- `paymentStatus`

Repository method:

```java
Optional<OrderEntity> findByOrderId(String orderId);
```

### `RefundPolicy` (table `refund_policy`)

Entity fields:

- `id` — primary key
- `orderStatus` — the order status this policy applies to
- `eligible` — whether that status is refund-eligible
- `message` — the customer-facing policy explanation

Repository method:

```java
Optional<RefundPolicy> findByOrderStatus(String orderStatus);
```

### `ShipmentTracking` (table `shipment_tracking`)

Entity fields:

- `id` — primary key
- `orderId` — the order this tracking record belongs to (`order_id` column)
- `carrier`
- `trackingNumber` (`tracking_number` column)
- `currentLocation` (`current_location` column)
- `estimatedDelivery` (`estimated_delivery` column)
- `shipmentStatus` (`shipment_status` column)

Repository method:

```java
Optional<ShipmentTracking> findByOrderId(String orderId);
```

## Order, refund and shipping logic

### `OrderService`

- `getOrderStatus(String orderId)`
  - fetches order details from the repository
  - throws `OrderNotFoundException` if the order is not found
- `cancelOrder(String orderId)`
  - loads the order
  - refuses cancellation if status is `Shipped` (throws `OrderCannotBeCancelledException`)
  - changes the status to `CANCELLED`, clears `expectedDelivery` and saves the record

### `RefundService`

- `checkEligibility(String orderStatus)`
  - looks up the refund policy for the given order status
  - throws `RefundPolicyNotFoundException` if no policy row exists

### `ShippingService`

- `getTracking(String orderId)`
  - looks up the shipment tracking record for the order
  - throws `ShipmentTrackingNotFoundException` if no record exists (e.g. the order has not shipped yet)

All four exceptions implement LangChain4j's `ToolErrorVisibleToLlm`, so when they
escape a tool, only a curated, user-facing message is sent to the LLM instead of
raw internal error details.

### Tools

The tools are annotated with `@Tool`, which tells LangChain4j that these Java methods are callable by the AI model:

- `OrderTools.getOrderStatus(String orderId)` — used by `OrderAgent` and `OrderSupportAIAgent`
- `OrderTools.cancelOrder(String orderId)` — used by `OrderSupportAIAgent`
- `RefundTool.checkRefundEligibility(String orderStatus)` — used by `RefundAgent`
- `ShippingTools.trackShipment(String orderId)` — used by `ShippingAgent`; returns a formatted tracking summary (carrier, tracking number, status, location, estimated delivery)

## Tool error handling

`ToolErrorHandlingConfig` declares two beans:

- `ToolArgumentsErrorHandler` — sends argument errors (invalid JSON, missing parameters) back to the LLM so it can self-correct
- `ToolExecutionErrorHandler` — lets only `ToolErrorVisibleToLlm` exceptions reach the LLM; everything else fails the invocation so bugs and infrastructure errors surface as errors instead of leaking internal details

The Spring Boot starter wires these automatically into every `@AiService` bean. Because the workflow and supervisor agents are built programmatically with `AgenticServices` (not covered by the starter), `AgentConfig` passes the same handlers explicitly to `OrderAgent`, `ShippingAgent` and `RefundAgent`.

## Agent definitions

### Sub-agents (LangChain4j Agentic API)

`OrderAgent` — prompt template extracts the order ID from `{{request}}`, calls the order tool, returns order details (output key `orderDetails`). Its `@Agent` description tells the supervisor to route order-status/product/delivery-detail questions here.

`ShippingAgent` — receives `{{request}}` and `{{orderDetails}}`, extracts the order ID and calls the shipping tool, returns shipping-related details (output key `shippingDetails`). Routed tracking/carrier/delivery-date questions.

`RefundAgent` — receives `{{request}}` and `{{orderDetails}}`, determines the order status, calls the refund tool, returns the decision (output key `refundDecision`). Routed refund-eligibility questions.

`ResponseAgent` — receives `{{request}}`, `{{orderDetails}}`, `{{shippingDetails}}` and `{{refundDecision}}`, returns the final customer answer (output key `finalResponse`). Its prompt instructs it to include every explicitly requested detail (e.g. a tracking number), never invent information, and never mention refunds unless asked.

Each sub-agent writes its output into the shared agentic scope; the next agent reads it via `@V` template variables, which is how data flows through both compositions.

### Static workflow (`WorkflowSupportAgent`)

Assembled in `AgentConfig`:

```java
AgenticServices
        .sequenceBuilder(WorkflowSupportAgent.class)
        .subAgents(orderAgent, shippingAgent, refundAgent, responseAgent)
        .outputKey("finalResponse")
        .listener(new DebugAgentListener())
        .build();
```

### Dynamic supervisor (`SupervisorAgent`)

Assembled in `AgentConfig`:

```java
AgenticServices
        .supervisorBuilder()
        .chatModel(chatModel)
        .subAgents(orderAgent, refundAgent, responseAgent, shippingAgent)
        .responseStrategy(SupervisorResponseStrategy.LAST)
        .listener(new DebugAgentListener())
        .build();
```

`SupervisorResponseStrategy` options: `LAST` (return the last sub-agent's response — used here), `SUMMARY` (summarize the whole interaction) and `SCORED` (an internal LLM scores the candidate responses against the original request and returns the best one). The supervisor re-plans after every sub-agent response and is bounded by `maxAgentsInvocations` (default 10) to prevent runaway loops.

### `@AiService` agents

`JavaTechieAIAgent` — general-purpose chat agent with an identity system message:

```java
@SystemMessage("""
        You are JavaTechie Assistant, a helpful and concise AI assistant.

        You are powered by the GLM model (glm-5.3-flash, trained by Z.ai),
        served through the OpenCode Go API. If asked who you are or which
        model you run on, answer truthfully based on this information.
        """)
String chat(String message);
```

Without this system message, models cannot reliably self-identify.

`OrderSupportAIAgent` — the single-agent alternative to the multi-agent compositions: an ecommerce support assistant with access to the order tools, instructed never to invent order status.

## REST endpoints

### General chat endpoint

```http
GET /api/ai-support?message=hello
```

Sends a text prompt to the generic AI agent (no tools).

### Order support endpoint

```http
GET /api/ai-support/order?message=What is the status of order ORD-1001?
```

Routes the message through the single order support agent with access to order tools.

### Static multi-agent workflow endpoint

```http
GET /api/ai-support/workflow?request=Can I get a refund for my order ORD-1001?
```

Runs the fixed `OrderAgent` → `ShippingAgent` → `RefundAgent` → `ResponseAgent` pipeline and returns the final response.

### Supervisor dynamic-routing endpoint

```http
GET /api/ai-support/supervisor?request=What is the tracking number for my order ORD-1003?
```

The supervisor LLM decides which agents to invoke for this specific request and returns the last invoked agent's response. Try the same question against `/workflow` and `/supervisor` and compare the console logs: the static pipeline runs all four agents, the supervisor only runs the ones it needs.

## Example usage

### Start the app

```bash
export OPENCODE_API_KEY=<your-opencode-go-api-key>
mvn spring-boot:run
```

### Example requests

```bash
curl "http://localhost:8080/api/ai-support?message=How%20are%20you?"
curl "http://localhost:8080/api/ai-support/order?message=What%20is%20the%20status%20of%20order%20ORD-1001?"
curl "http://localhost:8080/api/ai-support/workflow?request=Can%20I%20get%20a%20refund%20for%20my%20order%20ORD-1001%3F"
curl "http://localhost:8080/api/ai-support/supervisor?request=What%20is%20the%20tracking%20number%20for%20my%20order%20ORD-1003%3F"
```

## Required MySQL setup

Docker Compose starts MySQL automatically when the Spring Boot application starts (Docker Compose and a running Docker daemon must be installed). The `compose.yml` service uses the same database name and credentials as `application.yaml`; the named `mysql_data` volume retains database files across container restarts and recreation.

You can also start the database manually:

```sql
CREATE DATABASE javatechie;
USE javatechie;
```

Hibernate will create the `orders`, `refund_policy` and `shipment_tracking` tables automatically due to `ddl-auto: update` (with `PhysicalNamingStrategyStandardImpl`, unannotated field names become column names as-is).

### Seed data

Sample data (orders + refund policies + shipment tracking) is inserted automatically at startup from `src/main/resources/data.sql`, enabled by `spring.sql.init.mode: always` in `application.yaml` together with `spring.jpa.defer-datasource-initialization: true` (the script runs after Hibernate has created the tables). The script is idempotent — it uses `INSERT IGNORE` for `orders` (unique `order_id`) and `WHERE NOT EXISTS` guards for `refund_policy` and `shipment_tracking` — so restarts never duplicate rows and API-test mutations (e.g. cancelled orders) survive reboots.

The seeded `order_id` values match the Postman collection variables (`ORD-1001` … `ORD-1006`), so `langchain4j-dynamic-agent-routing.postman_collection.json` and `request.http` work out of the box:

| order_id  | status     | Refund eligible | Cancellable | Tracking row | Purpose |
|-----------|------------|-----------------|-------------|--------------|---------|
| ORD-1001  | Shipped    | No              | No          | Yes (BlueDart, in transit) | Refund/cancel negative case; shipping question |
| ORD-1002  | Processing | Yes             | Yes         | No           | Cancel + full-refund case; `ShipmentTrackingNotFoundException` case |
| ORD-1003  | Shipped    | No              | No          | Yes (Delhivery, in transit) | Cancel-shipped error case; tracking-number assertions |
| ORD-1004  | DELIVERED  | No              | Yes         | Yes (FedEx, delivered) | 30-day return-window case; compound supervisor request |
| ORD-1005  | CANCELLED  | Yes             | Yes         | No           | Refund-after-cancel case |
| ORD-1006  | Packed     | No policy row   | Yes         | No           | `RefundPolicyNotFoundException` case |

To reset the data to its seed state, wipe the MySQL volume (`docker compose down --volumes`) or delete the rows manually and restart the app.

## Build and run commands

### Compile the project

```bash
mvn clean compile
```

### Run tests

```bash
mvn test
```

### Start locally

```bash
export OPENCODE_API_KEY=<your-opencode-go-api-key>
mvn spring-boot:run
```

Spring Boot's Docker Compose integration starts the MySQL service from `compose.yml` before the application and stops it when the application shuts down. To manage the database separately, use `docker compose up -d` and `docker compose down`; the named volume remains intact when containers are removed. To permanently remove stored database data, explicitly remove the volume with `docker compose down --volumes`.

## Testing

### Automated test

The repository contains a minimal Spring Boot test:

```java
@SpringBootTest
class Langchain4JExampleApplicationTests {
    @Test
    void contextLoads() {
    }
}
```

This verifies that the Spring application context loads successfully.

### Manual API testing

Two ready-made assets cover every endpoint, tool-triggering scenario and negative case:

- `request.http` — for the IntelliJ HTTP Client and VS Code REST Client
- `langchain4j-dynamic-agent-routing.postman_collection.json` — for Postman, with response assertions (including tracking-number content assertions)

Watch the app console while running them:

- `/workflow` requests should produce four `=== AGENT START ===` / `=== AGENT END ===` pairs plus `TOOL CALLED -> ...` lines for the order, shipping and refund tools
- `/supervisor` requests should produce only the pairs for the agents the supervisor actually selected — compare with the same question on `/workflow` to see dynamic routing in action

## Notes and best practices

- Keep API keys out of source control.
- Use environment variables or secret management in production.
- Real-world systems should validate user input and enforce authorization.
- For production use, add logging, circuit breakers, retries, better error handling, and request validation.
- Keep each sub-agent small and focused — one responsibility per agent keeps prompts, tools and supervisor descriptions easy to reason about.
- Write precise `@Agent` descriptions: they are the routing signal the supervisor LLM relies on, so vague descriptions produce wrong routing decisions.
- Prefer the static sequence for fixed, auditable business processes; prefer a supervisor for open-ended conversations where the set of required agents varies per request.
- The project is a demo and intentionally keeps the order, refund and shipping operations simple.

## Common issues

### 1. MySQL connection failure

Check:
- MySQL is running locally
- database name is `javatechie`
- username/password match your local configuration
- driver is available via Maven dependency

### 2. OpenCode Go API key missing

Set the `OPENCODE_API_KEY` environment variable (an OpenCode Go subscription key) before starting the app.

### 3. Port already in use

The app uses the Spring Boot default port `8080`. If it is occupied, add a `server.port` override in `application.yaml`:

```yaml
server.port: 8081
```

### 4. Refund tool answers "no policy defined"

The `refund_policy` table has no row for the order's status — insert one (see the sample data above). Unknown statuses throw `RefundPolicyNotFoundException`, which the agent relays as a curated message.

### 5. Shipping tool answers "no tracking information"

The `shipment_tracking` table has no row for the order — typically because the order has not shipped yet (see ORD-1002/ORD-1005/ORD-1006 in the seed data). `ShipmentTrackingNotFoundException` is relayed to the LLM as a curated message.

### 6. Supervisor picks the wrong agent

The supervisor routes based on the `@Agent` descriptions and the request wording. Re-phrased or ambiguous questions can be mis-routed — refine the description of the relevant agent (or the agent prompts) rather than adding hard-coded routing rules.

## References and official documentation

1. LangChain4j official documentation: https://docs.langchain4j.dev/

2. LangChain4j Spring Boot integration: https://docs.langchain4j.dev/integrations/spring-boot/

3. LangChain4j AI Services: https://docs.langchain4j.dev/tutorials/ai-services

4. LangChain4j Tools — Error Handling: https://docs.langchain4j.dev/tutorials/tools#error-handling

5. Spring Boot official docs: https://spring.io/projects/spring-boot

6. Spring Data JPA docs: https://spring.io/projects/spring-data-jpa

7. OpenCode Go (model API used by this project): https://opencode.ai/v2/docs/console/go/

8. OpenAI API documentation (for the fallback OpenAI configuration): https://platform.openai.com/docs/api-reference

9. MySQL official docs: https://dev.mysql.com/doc/

10. Maven official website: https://maven.apache.org/

11. Java 21 documentation: https://docs.oracle.com/en/java/javase/21/

12. JPA/Hibernate reference: https://hibernate.org/orm/

## Summary

This project demonstrates dynamic agent routing with the LangChain4j Agentic API, Spring Boot and MySQL. A supervisor LLM routes each customer request to only the specialised agents it needs — order, shipping, refund and response — while a parallel static workflow endpoint shows the classic fixed-pipeline alternative over the same agents. Every agent is grounded in real database data through tools, observable end-to-end via an agent listener, and protected by curated tool error handling — a useful starting point for building production-grade customer support systems and adaptive agent orchestration.
