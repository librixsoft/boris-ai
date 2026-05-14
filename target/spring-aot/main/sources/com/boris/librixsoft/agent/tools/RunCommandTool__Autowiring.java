package com.boris.librixsoft.agent.tools;

import org.springframework.aot.generate.Generated;
import org.springframework.beans.factory.aot.AutowiredFieldValueResolver;
import org.springframework.beans.factory.support.RegisteredBean;

/**
 * Autowiring for {@link RunCommandTool}.
 */
@Generated
public class RunCommandTool__Autowiring {
  /**
   * Apply the autowiring.
   */
  public static RunCommandTool apply(RegisteredBean registeredBean, RunCommandTool instance) {
    AutowiredFieldValueResolver.forRequiredField("borisProperties").resolveAndSet(registeredBean, instance);
    return instance;
  }
}
