package org.stsci.astro.catalog.annotation;

import io.swagger.v3.oas.annotations.extensions.Extension;
import io.swagger.v3.oas.annotations.extensions.ExtensionProperty;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks an API endpoint as internal (service-to-service communication).
 * Internal APIs may have different stability guarantees and should not
 * be used by external consumers.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Extension(properties = {
        @ExtensionProperty(name = "x-api-audience", value = "internal"),
        @ExtensionProperty(name = "x-stability", value = "experimental")
})
public @interface InternalApi {
    /**
     * Description of why this API is internal
     */
    String value() default "Service-to-service communication only";
}
