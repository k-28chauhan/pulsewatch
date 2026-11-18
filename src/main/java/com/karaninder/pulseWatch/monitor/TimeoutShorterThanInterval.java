package com.karaninder.pulseWatch.monitor;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A custom Bean Validation annotation. Put it on a CLASS (here: a record) when a rule involves
 * more than one field; field annotations like @Min can only look at their own field.
 * The actual logic lives in TimeoutShorterThanIntervalValidator.
 * <p>
 * The three attributes (message, groups, payload) are required by the Bean Validation spec.
 */
@Documented
@Constraint(validatedBy = TimeoutShorterThanIntervalValidator.class)
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface TimeoutShorterThanInterval {

	String message() default "must be shorter than the check interval";

	Class<?>[] groups() default {};

	Class<? extends Payload>[] payload() default {};
}
