package com.mk.movieticketbooking.support;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a class as a Spring Boot integration test running under the
 * {@code test} profile (in-memory H2, short hold TTL, disabled H2
 * console). Keeps the {@code @SpringBootTest} + {@code @ActiveProfiles}
 * boilerplate out of every test.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@SpringBootTest
@ActiveProfiles("test")
public @interface IntegrationTest {}
