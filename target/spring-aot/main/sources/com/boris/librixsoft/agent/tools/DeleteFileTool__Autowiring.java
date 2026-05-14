package com.boris.librixsoft.agent.tools;

import org.springframework.aot.generate.Generated;
import org.springframework.beans.factory.aot.AutowiredFieldValueResolver;
import org.springframework.beans.factory.support.RegisteredBean;

/**
 * Autowiring for {@link DeleteFileTool}.
 */
@Generated
public class DeleteFileTool__Autowiring {
  /**
   * Apply the autowiring.
   */
  public static DeleteFileTool apply(RegisteredBean registeredBean, DeleteFileTool instance) {
    AutowiredFieldValueResolver.forRequiredField("borisProperties").resolveAndSet(registeredBean, instance);
    return instance;
  }
}
