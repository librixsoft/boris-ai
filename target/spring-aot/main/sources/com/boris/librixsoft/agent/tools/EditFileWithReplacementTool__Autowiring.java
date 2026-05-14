package com.boris.librixsoft.agent.tools;

import org.springframework.aot.generate.Generated;
import org.springframework.beans.factory.aot.AutowiredFieldValueResolver;
import org.springframework.beans.factory.support.RegisteredBean;

/**
 * Autowiring for {@link EditFileWithReplacementTool}.
 */
@Generated
public class EditFileWithReplacementTool__Autowiring {
  /**
   * Apply the autowiring.
   */
  public static EditFileWithReplacementTool apply(RegisteredBean registeredBean,
      EditFileWithReplacementTool instance) {
    AutowiredFieldValueResolver.forRequiredField("borisProperties").resolveAndSet(registeredBean, instance);
    return instance;
  }
}
