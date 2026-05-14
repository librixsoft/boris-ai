package com.boris.librixsoft.agent.tools;

import org.springframework.aot.generate.Generated;
import org.springframework.beans.factory.aot.AutowiredFieldValueResolver;
import org.springframework.beans.factory.support.RegisteredBean;

/**
 * Autowiring for {@link EditFileTool}.
 */
@Generated
public class EditFileTool__Autowiring {
  /**
   * Apply the autowiring.
   */
  public static EditFileTool apply(RegisteredBean registeredBean, EditFileTool instance) {
    AutowiredFieldValueResolver.forRequiredField("codeFormatter").resolveAndSet(registeredBean, instance);
    AutowiredFieldValueResolver.forRequiredField("borisProperties").resolveAndSet(registeredBean, instance);
    return instance;
  }
}
