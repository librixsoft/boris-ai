package com.boris.librixsoft.agent.tools;

import org.springframework.aot.generate.Generated;
import org.springframework.beans.factory.aot.AutowiredFieldValueResolver;
import org.springframework.beans.factory.support.RegisteredBean;

/**
 * Autowiring for {@link ReadFileTool}.
 */
@Generated
public class ReadFileTool__Autowiring {
  /**
   * Apply the autowiring.
   */
  public static ReadFileTool apply(RegisteredBean registeredBean, ReadFileTool instance) {
    AutowiredFieldValueResolver.forRequiredField("borisProperties").resolveAndSet(registeredBean, instance);
    return instance;
  }
}
