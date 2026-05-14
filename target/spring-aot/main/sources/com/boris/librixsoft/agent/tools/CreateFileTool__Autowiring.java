package com.boris.librixsoft.agent.tools;

import org.springframework.aot.generate.Generated;
import org.springframework.beans.factory.aot.AutowiredFieldValueResolver;
import org.springframework.beans.factory.support.RegisteredBean;

/**
 * Autowiring for {@link CreateFileTool}.
 */
@Generated
public class CreateFileTool__Autowiring {
  /**
   * Apply the autowiring.
   */
  public static CreateFileTool apply(RegisteredBean registeredBean, CreateFileTool instance) {
    AutowiredFieldValueResolver.forRequiredField("codeFormatter").resolveAndSet(registeredBean, instance);
    AutowiredFieldValueResolver.forRequiredField("borisProperties").resolveAndSet(registeredBean, instance);
    return instance;
  }
}
