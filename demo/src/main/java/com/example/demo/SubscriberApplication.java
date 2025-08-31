/*
 *  Change log:
 *  0.0.1 - Initial working one
 *  0.0.2 - Added OTel custom instrumentation
 *  0.0.2.1 - Added attributes and system output for subscribed content
 */

package com.example.demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.jms.annotation.EnableJms;

@SpringBootApplication
@EnableJms
public class SubscriberApplication {

	public static void main(String[] args) {
		SpringApplication.run(SubscriberApplication.class, args);
	}

}
