package com.boris.librixsoft.agent.tools;

import org.springframework.aot.generate.Generated;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.InstanceSupplier;
import org.springframework.beans.factory.support.RootBeanDefinition;

/**
 * Bean definitions for {@link EditFileTool}.
 */
@Generated
public class EditFileTool__BeanDefinitions {
  /**
   * Get the bean definition for 'editFileTool'.
   */
  public static BeanDefinition getEditFileToolBeanDefinition() {
    RootBeanDefinition beanDefinition = new RootBeanDefinition(EditFileTool.class);
    InstanceSupplier<EditFileTool> instanceSupplier = InstanceSupplier.using(EditFileTool::new);
    instanceSupplier = instanceSupplier.andThen(EditFileTool__Autowiring::apply);
    beanDefinition.setInstanceSupplier(instanceSupplier);
    return beanDefinition;
  }
}
