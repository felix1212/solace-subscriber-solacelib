package com.example.demo;

import jakarta.jms.Message;
import jakarta.jms.TextMessage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Component;

// Solace OTel helper:
import com.solace.opentelemetry.javaagent.jms.SolaceJmsW3CTextMapGetter;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.*;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
// import io.opentelemetry.context.propagation.TextMapGetter;

@Component
public class ItemListener {

    @Value("${app.queue.name}")
    private String queueName;

    // OTel API only
    private static final Tracer TRACER =
    GlobalOpenTelemetry.get().getTracer("poc.solace.jms");

    private static final SolaceJmsW3CTextMapGetter GETTER =
    new SolaceJmsW3CTextMapGetter();    

    @JmsListener(destination = "${app.queue.name}")
    public void onMessage(Message message) throws Exception {
      // Extract upstream context (from publisher) using the Solace getter
      Context extracted = GlobalOpenTelemetry.get()
          .getPropagators().getTextMapPropagator()
          .extract(Context.current(), message, GETTER);
  
      String dest = (message.getJMSDestination() != null)
          ? message.getJMSDestination().toString() : "unknown";
      String body = (message instanceof TextMessage)
          ? ((TextMessage) message).getText() : "<non-text>";
  
      try (Scope parent = extracted.makeCurrent()) {
        Span recvSpan = TRACER.spanBuilder("solace receive")
            .setSpanKind(SpanKind.CONSUMER)
            .setAttribute("messaging.system", "solace")
            .setAttribute("messaging.destination.name", queueName)
            .setAttribute("current.method","ItemListener")
            .setAttribute("current.instrumentation","solace.library")
            .startSpan();
  
        try (Scope s = recvSpan.makeCurrent()) {
          System.out.println("Subscriber: " + dest);
          System.out.println("Result: " + body);
          // your processing...
        } catch (Exception e) {
          recvSpan.recordException(e);
          recvSpan.setStatus(StatusCode.ERROR, e.getMessage());
          throw e;
        } finally {
          recvSpan.end();
        }
      }
    }
}

    // // Extract W3C headers from JMS properties
    // private static final TextMapGetter<Message> JMS_GETTER = new TextMapGetter<>() {
    //     @Override public Iterable<String> keys(Message msg) { 
    //         try { 
    //             @SuppressWarnings("unchecked")
    //             java.util.Enumeration<String> names = (java.util.Enumeration<String>) msg.getPropertyNames();
    //             java.util.List<String> keys = new java.util.ArrayList<>();
    //             while (names.hasMoreElements()) {
    //                 keys.add(names.nextElement());
    //             }
    //             return keys;
    //         } catch (Exception e) { 
    //             return java.util.List.of(); 
    //         } 
    //     }
    //     @Override public String get(Message msg, String key) {
    //     try { Object v = msg.getObjectProperty(key); return v != null ? v.toString() : null; }
    //     catch (Exception e) { return null; }
    //     }
    // };

    // @JmsListener(destination = "${app.queue.name}")
    // public void onMessage(Message message) throws Exception {
    //   String json = (message instanceof TextMessage) ? ((TextMessage) message).getText() : "<non-text>";
    //   String dest = (message.getJMSDestination() != null) ? message.getJMSDestination().toString() : "unknown";
  
    //   // Extract upstream context and start a CONSUMER span
    //   Context extracted = GlobalOpenTelemetry.get()
    //       .getPropagators().getTextMapPropagator()
    //       .extract(Context.current(), message, JMS_GETTER);
  
    //   try (Scope parent = extracted.makeCurrent()) {
    //     Span span = TRACER.spanBuilder("solace receive")
    //         .setSpanKind(SpanKind.CONSUMER)
    //         .setAttribute("messaging.system", "solace")
    //         .setAttribute("messaging.destination.name", dest)
    //         .startSpan();
  
    //     try (Scope s = span.makeCurrent()) {
    //       System.out.println("[Subscriber] " + dest + " => " + json);
    //       // ... your processing
    //     } catch (Exception e) {
    //       span.recordException(e);
    //       span.setStatus(StatusCode.ERROR, e.getMessage());
    //       throw e;
    //     } finally {
    //       span.end();
    //     }
    //   }
    // }
