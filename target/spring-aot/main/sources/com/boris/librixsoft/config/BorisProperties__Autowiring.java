package com.boris.librixsoft.config;

import org.springframework.aot.generate.Generated;
import org.springframework.beans.factory.aot.AutowiredFieldValueResolver;
import org.springframework.beans.factory.support.RegisteredBean;

/**
 * Autowiring for {@link BorisProperties}.
 */
@Generated
public class BorisProperties__Autowiring {
  /**
   * Apply the autowiring.
   */
  public static BorisProperties apply(RegisteredBean registeredBean, BorisProperties instance) {
    AutowiredFieldValueResolver.forRequiredField("serverPort").resolveAndSet(registeredBean, instance);
    return instance;
  }
}
