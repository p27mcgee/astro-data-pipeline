package com.mcgeecahill.astro.catalog.annotation;

import io.swagger.v3.oas.annotations.extensions.Extension;
import io.swagger.v3.oas.annotations.extensions.ExtensionProperty;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks an API endpoint as external (public-facing).
 * External APIs have strong backwards compatibility guarantees
 * and follow semantic versioning.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Extension(properties = {
        @ExtensionProperty(name = "x-api-audience", value = "external"),
        @ExtensionProperty(name = "x-stability", value = "stable")
})
public @interface ExternalApi {
    /**
     * Description of the external use case
     */
    String value() default "Public API - stable interface with versioning guarantees";
}