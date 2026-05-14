package com.boris.librixsoft.agent.tools;

import org.springframework.aot.generate.Generated;
import org.springframework.beans.factory.aot.AutowiredFieldValueResolver;
import org.springframework.beans.factory.support.RegisteredBean;

/**
 * Autowiring for {@link DeleteFolderTool}.
 */
@Generated
public class DeleteFolderTool__Autowiring {
  /**
   * Apply the autowiring.
   */
  public static DeleteFolderTool apply(RegisteredBean registeredBean, DeleteFolderTool instance) {
    AutowiredFieldValueResolver.forRequiredField("borisProperties").resolveAndSet(registeredBean, instance);
    return instance;
  }
}
