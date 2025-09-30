// /*
//  *  0.1 - First version
//  *  0.1.1 - Added a flag to control traceparent extraction source
//  */

// package com.example.demo;

// import jakarta.jms.Message;
// import jakarta.jms.TextMessage;
// import java.util.Enumeration;

// import org.springframework.beans.factory.annotation.Value;
// import org.springframework.jms.annotation.JmsListener;
// import org.springframework.stereotype.Component;
// import org.slf4j.Logger;
// import org.slf4j.LoggerFactory;

// // Solace OTel helper:
// import com.solace.opentelemetry.javaagent.jms.SolaceJmsW3CTextMapGetter;

// // OTel libraries:
// import io.opentelemetry.api.GlobalOpenTelemetry;
// import io.opentelemetry.api.trace.*;
// import io.opentelemetry.context.Context;
// import io.opentelemetry.context.Scope;


// @Component
// public class ItemListener {

//     private static final Logger logger = LoggerFactory.getLogger(ItemListener.class);

//     @Value("${app.queue.name}")
//     private String queueName;

//     @Value("${itemListenerDelay}")
//     private int itemListenerDelay;

//     @Value("${trace.context.source:jms-properties}")
//     private String traceContextSource;

//     // OTel API only
//     private static final Tracer TRACER =
//     GlobalOpenTelemetry.get().getTracer("poc.solace.jms");

//     private static final SolaceJmsW3CTextMapGetter GETTER =
//     new SolaceJmsW3CTextMapGetter();    

//     @JmsListener(destination = "${app.queue.name}")
//     public void onMessage(Message message) throws Exception {
//       logger.info("==================== Message Processing Started ====================");
      
//       // Create a root span to cover the entire message processing
//       Span messageProcessingSpan = TRACER.spanBuilder("message-processing")
//           .setSpanKind(SpanKind.CONSUMER)
//           .setAttribute("messaging.system", "solace")
//           .setAttribute("messaging.destination.name", queueName)
//           .setAttribute("component", "ItemListener")
//           .setAttribute("operation", "onMessage")
//           .startSpan();

//       try (Scope messageScope = messageProcessingSpan.makeCurrent()) {
//         // Trace: Message metadata extraction
//         Span metadataSpan = TRACER.spanBuilder("extract-message-metadata")
//             .setSpanKind(SpanKind.INTERNAL)
//             .startSpan();
        
//         try (Scope metadataScope = metadataSpan.makeCurrent()) {
//           logger.debug("Extracting message metadata");
          
//           // Debug: Show message type and basic info
//           logger.debug("Message type: {}", message.getClass().getSimpleName());
//           logger.debug("Message ID: {}", message.getJMSMessageID());
//           logger.debug("Message timestamp: {}", message.getJMSTimestamp());
          
//           // Debug: Show all JMS message properties
//           logger.debug("========== JMS Message Properties ==========");
//           try {
//             Enumeration<?> propertyNames = message.getPropertyNames();
//             while (propertyNames.hasMoreElements()) {
//               String propertyName = (String) propertyNames.nextElement();
//               Object propertyValue = message.getObjectProperty(propertyName);
//               logger.debug("JMS Property - {}: {}", propertyName, propertyValue);
//             }
//           } catch (Exception e) {
//             logger.error("Error reading JMS properties: {}", e.getMessage());
//             metadataSpan.recordException(e);
//           }
          
//           metadataSpan.setAttribute("message.type", message.getClass().getSimpleName());
//           metadataSpan.setAttribute("message.id", message.getJMSMessageID());
//           metadataSpan.setAttribute("message.timestamp", message.getJMSTimestamp());
//         } finally {
//           metadataSpan.end();
//         }
        
//         // Trace: Context extraction
//         Span contextSpan = TRACER.spanBuilder("extract-trace-context")
//             .setSpanKind(SpanKind.INTERNAL)
//             .startSpan();
        
//         Context extracted = Context.current();
        
//         try (Scope contextScope = contextSpan.makeCurrent()) {
//           logger.debug("========== Trace Context Extraction ==========");
//           logger.debug("Using SolaceJmsW3CTextMapGetter to extract W3C trace context...");
//           logger.debug("Current context before extraction: {}", Context.current());
          
//           // Extract upstream context (from publisher) using the Solace getter
//           extracted = GlobalOpenTelemetry.get()
//               .getPropagators().getTextMapPropagator()
//               .extract(Context.current(), message, GETTER);
          
//           logger.debug("Extracted context: {}", extracted);
//           logger.debug("W3C trace context extraction completed");
          
//           // Check JMS properties for traceparent first - if found, use it for end-to-end tracing
//           // Otherwise, fall back to header extraction
//           String traceparent = null;
//           String tracestate = null;
          
//           try {
//             logger.info("========== Trace Context Extraction Configuration ==========");
//             logger.info("Configured trace context source: {}", traceContextSource);
            
//             if ("jms-properties".equalsIgnoreCase(traceContextSource)) {
//               // Extract from JMS properties (follow publisher trace)
//               logger.info("Extracting trace context from JMS properties (publisher trace)");
              
//               traceparent = message.getStringProperty("traceparent");
//               tracestate = message.getStringProperty("tracestate");
              
//               // Log all JMS properties content for debugging
//               logger.info("========== JMS Properties Content ==========");
//               logger.info("traceparent from JMS property: {}", traceparent);
//               logger.info("tracestate from JMS property: {}", tracestate);
              
//               if (traceparent != null) {
//                 // Found traceparent in JMS properties - use it for end-to-end tracing
//                 logger.info("Found traceparent in JMS properties: {}", traceparent);
//                 if (tracestate != null) {
//                   logger.info("Found tracestate in JMS properties: {}", tracestate);
//                 }
                
//                 // Create a custom text map with the trace context from JMS properties
//                 java.util.Map<String, String> traceContextMap = new java.util.HashMap<>();
//                 traceContextMap.put("traceparent", traceparent);
//                 if (tracestate != null) {
//                   traceContextMap.put("tracestate", tracestate);
//                 }
                
//                 logger.info("Created trace context map from JMS properties: {}", traceContextMap);
                
//                 // Extract context using the custom map with proper TextMapGetter
//                 Context extractedFromProperties = GlobalOpenTelemetry.get()
//                     .getPropagators().getTextMapPropagator()
//                     .extract(Context.current(), traceContextMap, new io.opentelemetry.context.propagation.TextMapGetter<java.util.Map<String, String>>() {
//                       @Override
//                       public java.lang.Iterable<String> keys(java.util.Map<String, String> carrier) {
//                         return carrier.keySet();
//                       }
                      
//                       @Override
//                       public String get(java.util.Map<String, String> carrier, String key) {
//                         return carrier.get(key);
//                       }
//                     });
                
//                 logger.info("Successfully extracted context from JMS properties: {}", extractedFromProperties);
                
//                 // Extract and log trace ID from the extracted context
//                 if (extractedFromProperties != null) {
//                   SpanContext spanContext = Span.fromContext(extractedFromProperties).getSpanContext();
//                   if (spanContext.isValid()) {
//                     logger.info("========== Trace ID from JMS Properties ==========");
//                     logger.info("Extracted Trace ID: {}", spanContext.getTraceId());
//                     logger.info("Extracted Span ID: {}", spanContext.getSpanId());
//                     logger.info("Is Remote: {}", spanContext.isRemote());
//                     logger.info("Is Sampled: {}", spanContext.isSampled());
//                   } else {
//                     logger.warn("Extracted context from JMS properties is not valid");
//                   }
//                 }
                
//                 // Use the context from JMS properties for end-to-end tracing
//                 extracted = extractedFromProperties;
//                 logger.info("Using trace context from JMS properties for end-to-end tracing");
//                 contextSpan.setAttribute("trace.source", "jms-properties");
//               } else {
//                 // No traceparent in JMS properties - fall back to header extraction
//                 logger.warn("No traceparent found in JMS properties, falling back to header extraction");
//                 logger.info("Using trace context from headers");
//                 logger.info("Trace context from headers: {}", extracted);
                
//                 // Extract and log trace ID from header extraction
//                 if (extracted != null) {
//                   SpanContext spanContext = Span.fromContext(extracted).getSpanContext();
//                   if (spanContext.isValid()) {
//                     logger.info("========== Trace ID from Headers (Fallback) ==========");
//                     logger.info("Header Trace ID: {}", spanContext.getTraceId());
//                     logger.info("Header Span ID: {}", spanContext.getSpanId());
//                     logger.info("Is Remote: {}", spanContext.isRemote());
//                     logger.info("Is Sampled: {}", spanContext.isSampled());
//                   } else {
//                     logger.warn("Extracted context from headers is not valid");
//                   }
//                 }
                
//                 contextSpan.setAttribute("trace.source", "headers-fallback");
//               }
//             } else if ("headers".equalsIgnoreCase(traceContextSource)) {
//               // Extract from headers (follow Solace trace)
//               logger.info("Extracting trace context from headers (Solace trace)");
//               logger.info("Using trace context from headers");
//               logger.info("Trace context from headers: {}", extracted);
              
//               // Extract and log trace ID from header extraction
//               if (extracted != null) {
//                 SpanContext spanContext = Span.fromContext(extracted).getSpanContext();
//                 if (spanContext.isValid()) {
//                   logger.info("========== Trace ID from Headers ==========");
//                   logger.info("Header Trace ID: {}", spanContext.getTraceId());
//                   logger.info("Header Span ID: {}", spanContext.getSpanId());
//                   logger.info("Is Remote: {}", spanContext.isRemote());
//                   logger.info("Is Sampled: {}", spanContext.isSampled());
//                 } else {
//                   logger.warn("Extracted context from headers is not valid");
//                 }
//               }
              
//               contextSpan.setAttribute("trace.source", "headers");
//             } else {
//               // Invalid configuration - fall back to JMS properties
//               logger.warn("Invalid trace context source configuration: {}, falling back to JMS properties", traceContextSource);
              
//               traceparent = message.getStringProperty("traceparent");
//               tracestate = message.getStringProperty("tracestate");
              
//               if (traceparent != null) {
//                 logger.info("Found traceparent in JMS properties: {}", traceparent);
                
//                 java.util.Map<String, String> traceContextMap = new java.util.HashMap<>();
//                 traceContextMap.put("traceparent", traceparent);
//                 if (tracestate != null) {
//                   traceContextMap.put("tracestate", tracestate);
//                 }
                
//                 Context extractedFromProperties = GlobalOpenTelemetry.get()
//                     .getPropagators().getTextMapPropagator()
//                     .extract(Context.current(), traceContextMap, new io.opentelemetry.context.propagation.TextMapGetter<java.util.Map<String, String>>() {
//                       @Override
//                       public java.lang.Iterable<String> keys(java.util.Map<String, String> carrier) {
//                         return carrier.keySet();
//                       }
                      
//                       @Override
//                       public String get(java.util.Map<String, String> carrier, String key) {
//                         return carrier.get(key);
//                       }
//                     });
                
//                 extracted = extractedFromProperties;
//                 logger.info("Using trace context from JMS properties (fallback)");
//                 contextSpan.setAttribute("trace.source", "jms-properties-fallback");
//               } else {
//                 logger.info("No traceparent found in JMS properties, using header extraction result");
//                 contextSpan.setAttribute("trace.source", "headers-fallback");
//               }
//             }
//           } catch (Exception e) {
//             logger.error("Error reading trace context: {}", e.getMessage());
//             logger.debug("Using header extraction result: {}", extracted);
//             contextSpan.recordException(e);
//             contextSpan.setAttribute("trace.source", "error-fallback");
//           }
          
//           contextSpan.setAttribute("traceparent.found", traceparent != null);
//           if (traceparent != null) {
//             contextSpan.setAttribute("traceparent.value", traceparent);
//           }
//         } finally {
//           contextSpan.end();
//         }
        
//         // Trace: Message data retrieval
//         Span dataSpan = TRACER.spanBuilder("extract-message-data")
//             .setSpanKind(SpanKind.INTERNAL)
//             .startSpan();
        
//         try (Scope dataScope = dataSpan.makeCurrent()) {
//           logger.debug("========== Message Data Retrieval ==========");
//           String dest = (message.getJMSDestination() != null)
//               ? message.getJMSDestination().toString() : "unknown";
//           logger.debug("JMS Destination: {}", dest);
          
//           String body = (message instanceof TextMessage)
//               ? ((TextMessage) message).getText() : "<non-text>";
//           logger.debug("Message body type: {}", (message instanceof TextMessage ? "TextMessage" : "Other"));
//           logger.debug("Message body content: {}", body);
//           logger.debug("Message body length: {} characters", (body != null ? body.length() : 0));
          
//           dataSpan.setAttribute("message.destination", dest);
//           dataSpan.setAttribute("message.body.type", message instanceof TextMessage ? "TextMessage" : "Other");
//           dataSpan.setAttribute("message.body.length", body != null ? body.length() : 0);
//           if (body != null && body.length() < 1000) { // Only log short messages
//             dataSpan.setAttribute("message.body.content", body);
//           }
//         } finally {
//           dataSpan.end();
//         }
        
//         // Trace: Message processing with extracted context
//         Span processingSpan = TRACER.spanBuilder("process-message")
//             .setSpanKind(SpanKind.INTERNAL)
//             .startSpan();
        
//         try (Scope processingScope = processingSpan.makeCurrent()) {
//           logger.debug("========== OpenTelemetry Span Creation ==========");
          
//           // Handle extracted context safely
//           if (extracted != null) {
//             try (Scope parent = extracted.makeCurrent()) {
//               logger.debug("Making extracted context current...");
//               logger.debug("Current context after making extracted context current: {}", Context.current());
              
//               // Log the final trace ID that will be used for processing
//               SpanContext finalSpanContext = Span.fromContext(Context.current()).getSpanContext();
//               if (finalSpanContext.isValid()) {
//                 logger.info("========== Final Processing Trace ID ==========");
//                 logger.info("Final Trace ID for processing: {}", finalSpanContext.getTraceId());
//                 logger.info("Final Span ID for processing: {}", finalSpanContext.getSpanId());
//                 logger.info("Is Remote: {}", finalSpanContext.isRemote());
//                 logger.info("Is Sampled: {}", finalSpanContext.isSampled());
//               } else {
//                 logger.warn("Final processing context is not valid");
//               }
//             }
//           } else {
//             logger.warn("No extracted context available, using current context");
//             logger.debug("Current context: {}", Context.current());
//           }

//           // Simulate processing delay
//           Thread.sleep(itemListenerDelay);
          
//           // Create span as child of the extracted context
//           Span recvSpan = TRACER.spanBuilder("solace receive")
//               .setSpanKind(SpanKind.CONSUMER)
//               .setAttribute("messaging.system", "solace")
//               .setAttribute("messaging.destination.name", queueName)
//               .setAttribute("current.method","ItemListener")
//               .setAttribute("current.instrumentation","solace.library")
//               .setParent(extracted) // Set the extracted context as parent
//               .startSpan();
          
//           logger.debug("Created span: {}", recvSpan);
//           logger.debug("Span context: {}", recvSpan.getSpanContext());
//           logger.debug("Trace ID: {}", recvSpan.getSpanContext().getTraceId());
//           logger.debug("Span ID: {}", recvSpan.getSpanContext().getSpanId());
          
//           // Log the new span's trace information
//           logger.info("========== New Span Trace Information ==========");
//           logger.info("New Span Trace ID: {}", recvSpan.getSpanContext().getTraceId());
//           logger.info("New Span ID: {}", recvSpan.getSpanContext().getSpanId());
//           logger.info("New Span Is Remote: {}", recvSpan.getSpanContext().isRemote());
//           logger.info("New Span Is Sampled: {}", recvSpan.getSpanContext().isSampled());
          
//           // Log parent context information
//           if (extracted != null) {
//             SpanContext parentContext = Span.fromContext(extracted).getSpanContext();
//             if (parentContext.isValid()) {
//               logger.info("========== Parent Context Information ==========");
//               logger.info("Parent Trace ID: {}", parentContext.getTraceId());
//               logger.info("Parent Span ID: {}", parentContext.getSpanId());
//               logger.info("Parent Is Remote: {}", parentContext.isRemote());
//               logger.info("Parent Is Sampled: {}", parentContext.isSampled());
//               logger.info("Span is child of parent: {}", 
//                   recvSpan.getSpanContext().getTraceId().equals(parentContext.getTraceId()));
//             }
//           }
    
//           try (Scope s = recvSpan.makeCurrent()) {
//             logger.debug("========== Processing Message ==========");
//             String dest = (message.getJMSDestination() != null)
//                 ? message.getJMSDestination().toString() : "unknown";
//             String body = (message instanceof TextMessage)
//                 ? ((TextMessage) message).getText() : "<non-text>";
            
//             logger.info("Subscriber: {}", dest);
//             logger.info("Result: {}", body);
//             logger.debug("Message processing completed successfully");
            
//             recvSpan.setAttribute("processing.status", "success");
//             recvSpan.setAttribute("processing.destination", dest);
//             recvSpan.setAttribute("processing.body.length", body != null ? body.length() : 0);
            
//             // your processing...
//           } catch (Exception e) {
//             logger.error("ERROR during message processing: {}", e.getMessage());
//             recvSpan.recordException(e);
//             recvSpan.setStatus(StatusCode.ERROR, e.getMessage());
//             processingSpan.recordException(e);
//             processingSpan.setStatus(StatusCode.ERROR, e.getMessage());
//             throw e;
//           } finally {
//             logger.debug("Ending span...");
//             recvSpan.end();
//             logger.debug("Span ended");
//           }
//         } finally {
//           processingSpan.end();
//         }
        
//         messageProcessingSpan.setAttribute("processing.status", "success");
//       } catch (Exception e) {
//         logger.error("Error in message processing: {}", e.getMessage());
//         messageProcessingSpan.recordException(e);
//         messageProcessingSpan.setStatus(StatusCode.ERROR, e.getMessage());
//         throw e;
//       } finally {
//         messageProcessingSpan.end();
//         logger.info("==================== Message Processing Completed ====================");
//       }
//     }
// }