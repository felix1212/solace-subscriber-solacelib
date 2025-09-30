// /*
//  *  0.1 - First version
//  *  0.1.1 - Added a flag to control traceparent extraction source
//  *  0.2.0 - Updated logic to ItemListen2.java
//  *  0.2.1 - Modified to try making root span following upstream solace (message-processing span is the root span of whole processing)
//  */

package com.example.demo;

import jakarta.jms.Message;
import jakarta.jms.TextMessage;
import java.util.Enumeration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// Solace OTel helper:
import com.solace.opentelemetry.javaagent.jms.SolaceJmsW3CTextMapGetter;

// OTel libraries:
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.*;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;

@Component
public class ItemListener {

    private static final Logger logger = LoggerFactory.getLogger(ItemListener.class);

    @Value("${app.queue.name}")
    private String queueName;

    @Value("${itemListenerDelay}")
    private int itemListenerDelay;

    @Value("${trace.context.source:jms-properties}")
    private String traceContextSource;

    // OTel API only
    private static final Tracer TRACER =
        GlobalOpenTelemetry.get().getTracer("poc.solace.jms");

    private static final SolaceJmsW3CTextMapGetter GETTER =
        new SolaceJmsW3CTextMapGetter();

    @JmsListener(destination = "${app.queue.name}")
    public void onMessage(Message message) throws Exception {
        logger.info("==================== ItemListener2 Message Processing Started ====================");
        logger.info("Configured trace context source: {}", traceContextSource);
        
        // Extract trace context first to use as parent for all spans
        Context extractedContext = extractTraceContext(message);
        
        // Create a root span to cover the entire message processing
        Span messageProcessingSpan = TRACER.spanBuilder("message-processing")
            .setSpanKind(SpanKind.CONSUMER)
            .setAttribute("messaging.system", "solace")
            .setAttribute("messaging.destination.name", queueName)
            .setAttribute("component", "ItemListener2")
            .setAttribute("operation", "onMessage")
            .setAttribute("trace.context.source", traceContextSource)
            .setParent(extractedContext) // Follow upstream Solace trace
            .startSpan();

        try (Scope messageScope = messageProcessingSpan.makeCurrent()) {
            
            // Extract and output JMS properties and message payload
            extractAndOutputMessageInfo(message);
            
            // Process message with extracted context
            processMessageWithContext(message, extractedContext);
            
            messageProcessingSpan.setAttribute("processing.status", "success");
        } catch (Exception e) {
            logger.error("Error in message processing: {}", e.getMessage());
            messageProcessingSpan.recordException(e);
            messageProcessingSpan.setStatus(StatusCode.ERROR, e.getMessage());
            throw e;
        } finally {
            messageProcessingSpan.end();
            logger.info("==================== ItemListener2 Message Processing Completed ====================");
        }
    }

    private void extractAndOutputMessageInfo(Message message) throws Exception {
        logger.info("========== Message Information Extraction ==========");
        
        // Basic message info
        logger.info("Message type: {}", message.getClass().getSimpleName());
        logger.info("Message ID: {}", message.getJMSMessageID());
        logger.info("Message timestamp: {}", message.getJMSTimestamp());
        logger.info("JMS Destination: {}", 
            (message.getJMSDestination() != null) ? message.getJMSDestination().toString() : "unknown");
        
        // Extract and output JMS properties
        logger.info("========== JMS Properties ==========");
        try {
            Enumeration<?> propertyNames = message.getPropertyNames();
            boolean hasProperties = false;
            while (propertyNames.hasMoreElements()) {
                hasProperties = true;
                String propertyName = (String) propertyNames.nextElement();
                Object propertyValue = message.getObjectProperty(propertyName);
                logger.info("JMS Property - {}: {}", propertyName, propertyValue);
            }
            if (!hasProperties) {
                logger.info("No JMS properties found");
            }
        } catch (Exception e) {
            logger.error("Error reading JMS properties: {}", e.getMessage());
        }
        
        // Extract and output message payload
        logger.info("========== Message Payload ==========");
        if (message instanceof TextMessage) {
            String body = ((TextMessage) message).getText();
            logger.info("Message body type: TextMessage");
            logger.info("Message body length: {} characters", (body != null ? body.length() : 0));
            logger.info("Message body content: {}", body);
        } else {
            logger.info("Message body type: {}", message.getClass().getSimpleName());
            logger.info("Message body content: <non-text-message>");
        }
    }

    private Context extractTraceContext(Message message) {
        logger.info("========== Trace Context Extraction ==========");
        logger.info("Using trace context source: {}", traceContextSource);
        
        Context extractedContext = Context.current();
        
        if ("jms-properties".equalsIgnoreCase(traceContextSource)) {
            extractedContext = extractFromJmsProperties(message);
        } else if ("headers".equalsIgnoreCase(traceContextSource)) {
            extractedContext = extractFromHeaders(message);
        } else {
            logger.warn("Invalid trace context source: {}, falling back to JMS properties", traceContextSource);
            extractedContext = extractFromJmsProperties(message);
        }
        
        // Log final extracted context information
        if (extractedContext != null) {
            SpanContext spanContext = Span.fromContext(extractedContext).getSpanContext();
            if (spanContext.isValid()) {
                logger.info("========== Extracted Trace Context ==========");
                logger.info("Trace ID: {}", spanContext.getTraceId());
                logger.info("Span ID: {}", spanContext.getSpanId());
                logger.info("Is Remote: {}", spanContext.isRemote());
                logger.info("Is Sampled: {}", spanContext.isSampled());
            } else {
                logger.warn("Extracted context is not valid");
            }
        } else {
            logger.warn("No context extracted");
        }
        
        return extractedContext;
    }

    private Context extractFromJmsProperties(Message message) {
        logger.info("========== JMS Properties Trace Context Extraction ==========");
        
        try {
            String traceparent = message.getStringProperty("traceparent");
            String tracestate = message.getStringProperty("tracestate");
            
            logger.info("traceparent from JMS property: {}", traceparent);
            logger.info("tracestate from JMS property: {}", tracestate);
            
            if (traceparent != null) {
                logger.info("Found traceparent in JMS properties, creating context...");
                
                // Create a custom text map with the trace context from JMS properties
                java.util.Map<String, String> traceContextMap = new java.util.HashMap<>();
                traceContextMap.put("traceparent", traceparent);
                if (tracestate != null) {
                    traceContextMap.put("tracestate", tracestate);
                }
                
                logger.info("Created trace context map: {}", traceContextMap);
                
                // Extract context using the custom map
                Context extracted = GlobalOpenTelemetry.get()
                    .getPropagators().getTextMapPropagator()
                    .extract(Context.current(), traceContextMap, new io.opentelemetry.context.propagation.TextMapGetter<java.util.Map<String, String>>() {
                        @Override
                        public java.lang.Iterable<String> keys(java.util.Map<String, String> carrier) {
                            return carrier.keySet();
                        }
                        
                        @Override
                        public String get(java.util.Map<String, String> carrier, String key) {
                            return carrier.get(key);
                        }
                    });
                
                logger.info("Successfully extracted context from JMS properties");
                return extracted;
            } else {
                logger.warn("No traceparent found in JMS properties");
                return Context.current();
            }
        } catch (Exception e) {
            logger.error("Error extracting trace context from JMS properties: {}", e.getMessage());
            return Context.current();
        }
    }

    private Context extractFromHeaders(Message message) {
        logger.info("========== Headers Trace Context Extraction ==========");
        
        try {
            logger.info("Using SolaceJmsW3CTextMapGetter to extract W3C trace context from headers...");
            
            // Extract upstream context using the Solace getter
            Context extracted = GlobalOpenTelemetry.get()
                .getPropagators().getTextMapPropagator()
                .extract(Context.current(), message, GETTER);
            
            logger.info("Successfully extracted context from headers");
            return extracted;
        } catch (Exception e) {
            logger.error("Error extracting trace context from headers: {}", e.getMessage());
            return Context.current();
        }
    }

    private void processMessageWithContext(Message message, Context extractedContext) throws Exception {
        logger.info("========== Message Processing with Extracted Context ==========");
        
        // Simulate processing delay
        Thread.sleep(itemListenerDelay);
        
        // Create span as child of the extracted context
        Span processingSpan = TRACER.spanBuilder("solace receive")
            .setSpanKind(SpanKind.CONSUMER)
            .setAttribute("messaging.system", "solace")
            .setAttribute("messaging.destination.name", queueName)
            .setAttribute("current.method", "ItemListener2")
            .setAttribute("current.instrumentation", "solace.library")
            .setParent(extractedContext) // Set the extracted context as parent
            .startSpan();
        
        try (Scope processingScope = processingSpan.makeCurrent()) {
            logger.info("========== Processing Message ==========");
            
            String dest = (message.getJMSDestination() != null)
                ? message.getJMSDestination().toString() : "unknown";
            String body = (message instanceof TextMessage)
                ? ((TextMessage) message).getText() : "<non-text>";
            
            logger.info("Subscriber: {}", dest);
            logger.info("Result: {}", body);
            
            processingSpan.setAttribute("processing.status", "success");
            processingSpan.setAttribute("processing.destination", dest);
            processingSpan.setAttribute("processing.body.length", body != null ? body.length() : 0);
            
            // Log trace information for the processing span
            SpanContext spanContext = processingSpan.getSpanContext();
            logger.info("========== Processing Span Trace Information ==========");
            logger.info("Processing Span Trace ID: {}", spanContext.getTraceId());
            logger.info("Processing Span ID: {}", spanContext.getSpanId());
            logger.info("Processing Span Is Remote: {}", spanContext.isRemote());
            logger.info("Processing Span Is Sampled: {}", spanContext.isSampled());
            
            // Log parent context information
            if (extractedContext != null) {
                SpanContext parentContext = Span.fromContext(extractedContext).getSpanContext();
                if (parentContext.isValid()) {
                    logger.info("========== Parent Context Information ==========");
                    logger.info("Parent Trace ID: {}", parentContext.getTraceId());
                    logger.info("Parent Span ID: {}", parentContext.getSpanId());
                    logger.info("Parent Is Remote: {}", parentContext.isRemote());
                    logger.info("Parent Is Sampled: {}", parentContext.isSampled());
                    logger.info("Span is child of parent: {}", 
                        spanContext.getTraceId().equals(parentContext.getTraceId()));
                }
            }
            
        } catch (Exception e) {
            logger.error("ERROR during message processing: {}", e.getMessage());
            processingSpan.recordException(e);
            processingSpan.setStatus(StatusCode.ERROR, e.getMessage());
            throw e;
        } finally {
            processingSpan.end();
            logger.info("Processing span ended");
        }
    }
}
