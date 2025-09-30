# Solace OpenTelemetry Subscriber

A Spring Boot application that demonstrates OpenTelemetry tracing with Solace JMS messaging, supporting both manual and automatic instrumentation approaches.

## Overview

This project contains multiple implementations of a JMS message subscriber that can follow different trace contexts:

- **ItemListener.java**: Full manual OpenTelemetry instrumentation with trace context extraction
- **ItemListener3.java**: Clean implementation without manual instrumentation (for external agents)

## Features

### Trace Context Extraction
- **JMS Properties**: Follow publisher trace context from JMS message properties
- **Headers**: Follow Solace broker trace context from message headers
- **Configurable**: Switch between extraction methods via `application.properties`

### Message Processing
- JMS message consumption from Solace queues
- Comprehensive message metadata extraction and logging
- Configurable processing delays
- Error handling and logging

## Project Structure

```
demo/
├── src/main/java/com/example/demo/
│   ├── ItemListener.java          # Manual OTel instrumentation
│   ├── ItemListener3.java        # No manual instrumentation
│   ├── SubscriberApplication.java # Spring Boot main class
│   └── ServletInitializer.java   # Servlet configuration
├── src/main/resources/
│   └── application.properties     # Configuration
└── docker/
    ├── dockerfile                 # Container configuration
    └── *.war                     # Application artifacts
```

## Configuration

### Application Properties (`application.properties`)

```properties
# Solace JMS Configuration
solace.jms.host=smf://localhost:55555
solace.jms.msgVpn=default
solace.jms.clientUsername=app-sub
solace.jms.clientPassword=P@ss1234

# Queue Configuration
app.queue.name=hkjc-poc-queue-2

# Processing Configuration
itemListenerDelay=15000
server.port=24680

# Trace Context Extraction
# Options: "jms-properties" (follow publisher trace) or "headers" (follow Solace trace)
trace.context.source=headers
```

### Trace Context Sources

1. **`jms-properties`**: Extracts trace context from JMS message properties (follows publisher trace)
2. **`headers`**: Extracts trace context from message headers using SolaceJmsW3CTextMapGetter (follows Solace trace)

## Implementation Versions

### ItemListener.java (Manual Instrumentation)
- Full OpenTelemetry manual instrumentation
- Custom span creation and management
- Trace context extraction from JMS properties or headers
- Parent-child span relationships
- Comprehensive debugging logs

### ItemListener3.java (Automatic Instrumentation)
- No manual OpenTelemetry code
- Clean business logic only
- Relies on external agents for instrumentation
- Simplified message processing
- All logging preserved for debugging

## Dependencies

### Core Dependencies
- Spring Boot 3.x
- Spring JMS
- Solace JMS API
- OpenTelemetry Java API
- Solace OpenTelemetry Integration

### Key Libraries
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-jms</artifactId>
</dependency>
<dependency>
    <groupId>com.solace</groupId>
    <artifactId>solace-jms-spring-boot-starter</artifactId>
</dependency>
<dependency>
    <groupId>io.opentelemetry</groupId>
    <artifactId>opentelemetry-api</artifactId>
</dependency>
```

## Usage

### Running the Application

1. **Configure Solace Connection**:
   ```properties
   solace.jms.host=smf://your-solace-host:55555
   solace.jms.msgVpn=your-vpn
   solace.jms.clientUsername=your-username
   solace.jms.clientPassword=your-password
   ```

2. **Set Queue Name**:
   ```properties
   app.queue.name=your-queue-name
   ```

3. **Choose Trace Context Source**:
   ```properties
   # Follow publisher trace
   trace.context.source=jms-properties
   
   # Follow Solace trace
   trace.context.source=headers
   ```

4. **Run the Application**:
   ```bash
   mvn spring-boot:run
   ```

### Docker Deployment

```bash
# Build the application
mvn clean package

# Build Docker image
docker build -t solace-subscriber .

# Run with OpenTelemetry agent
docker run -p 24680:24680 \
  -e OTEL_SERVICE_NAME=solace-subscriber \
  -e OTEL_EXPORTER_OTLP_ENDPOINT=http://your-collector:4317 \
  solace-subscriber
```

## OpenTelemetry Configuration

### Manual Instrumentation (ItemListener.java)
- Creates custom spans for message processing
- Extracts trace context from JMS properties or headers
- Manages parent-child span relationships
- Provides detailed trace information logging

### Automatic Instrumentation (ItemListener3.java)
- Uses OpenTelemetry Java agent for automatic instrumentation
- No manual span creation required
- External agents handle trace context propagation
- Cleaner code with same functionality

## Logging and Debugging

### Comprehensive Logging
- Message metadata extraction
- JMS properties enumeration
- Message payload content
- Trace context extraction details
- Processing status and timing

### Debug Information
- Trace ID and Span ID logging
- Parent-child relationship verification
- Context source identification
- Error handling and exception logging

## Trace Context Flow

### JMS Properties Mode
```
Publisher App → JMS Properties → Subscriber (follows publisher trace)
```

### Headers Mode
```
Publisher App → Solace Broker → Headers → Subscriber (follows Solace trace)
```

## Monitoring and Observability

### Metrics
- Message processing duration
- Error rates and exceptions
- Queue consumption rates
- Trace context extraction success/failure

### Traces
- End-to-end message flow
- Span relationships and hierarchy
- Context propagation verification
- Performance bottlenecks identification

## Troubleshooting

### Common Issues

1. **"Unknown Queue" Error**:
   - Ensure queue exists in Solace
   - Verify queue name configuration
   - Check Solace connection settings

2. **Trace Context Not Found**:
   - Verify message contains trace headers/properties
   - Check trace context source configuration
   - Ensure OpenTelemetry agent is running

3. **Connection Issues**:
   - Verify Solace host and port
   - Check VPN and credentials
   - Ensure network connectivity

### Debug Logs
Enable debug logging to see detailed trace information:
```properties
logging.level.com.example.demo=DEBUG
logging.level.io.opentelemetry=DEBUG
```

## Development

### Adding New Features
1. Follow the existing pattern in `ItemListener.java`
2. Add comprehensive logging for debugging
3. Update configuration in `application.properties`
4. Test with both trace context sources

### Testing
- Test with different message types
- Verify trace context extraction
- Check span relationships
- Validate error handling

## Version History

- **0.1**: Initial version with basic JMS processing
- **0.1.1**: Added trace context source configuration
- **0.2.0**: Updated to ItemListener2.java with cleaner structure
- **0.2.1**: Modified root span to follow upstream Solace trace
- **0.3.0**: Created ItemListener3.java without manual instrumentation

## Contributing

1. Follow existing code patterns
2. Add comprehensive logging
3. Update documentation
4. Test with both instrumentation approaches

## License

This project is part of the HKJC POC for Solace OpenTelemetry integration.
