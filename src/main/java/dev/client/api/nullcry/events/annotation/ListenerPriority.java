package dev.client.api.nullcry.events.annotation;

import dev.client.api.nullcry.events.types.Priority;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks an {@link com.google.common.eventbus.Subscribe} method with an explicit execution priority.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface ListenerPriority {
    byte value() default Priority.MEDIUM;
}
