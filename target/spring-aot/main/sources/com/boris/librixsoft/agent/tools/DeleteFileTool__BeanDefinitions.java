package com.boris.librixsoft.agent.tools;

import org.springframework.aot.generate.Generated;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.InstanceSupplier;
import org.springframework.beans.factory.support.RootBeanDefinition;

/**
 * Bean definitions for {@link DeleteFileTool}.
 */
@Generated
public class DeleteFileTool__BeanDefinitions {
  /**
   * Get the bean definition for 'deleteFileTool'.
   */
  public static BeanDefinition getDeleteFileToolBeanDefinition() {
    RootBeanDefinition beanDefinition = new RootBeanDefinition(DeleteFileTool.class);
    InstanceSupplier<DeleteFileTool> instanceSupplier = InstanceSupplier.using(DeleteFileTool::new);
    instanceSupplier = instanceSupplier.andThen(DeleteFileTool__Autowiring::apply);
    beanDefinition.setInstanceSupplier(instanceSupplier);
    return beanDefinition;
  }
}
