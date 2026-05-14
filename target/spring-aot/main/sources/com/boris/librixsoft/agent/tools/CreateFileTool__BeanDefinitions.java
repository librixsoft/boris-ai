package com.boris.librixsoft.agent.tools;

import org.springframework.aot.generate.Generated;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.InstanceSupplier;
import org.springframework.beans.factory.support.RootBeanDefinition;

/**
 * Bean definitions for {@link CreateFileTool}.
 */
@Generated
public class CreateFileTool__BeanDefinitions {
  /**
   * Get the bean definition for 'createFileTool'.
   */
  public static BeanDefinition getCreateFileToolBeanDefinition() {
    RootBeanDefinition beanDefinition = new RootBeanDefinition(CreateFileTool.class);
    InstanceSupplier<CreateFileTool> instanceSupplier = InstanceSupplier.using(CreateFileTool::new);
    instanceSupplier = instanceSupplier.andThen(CreateFileTool__Autowiring::apply);
    beanDefinition.setInstanceSupplier(instanceSupplier);
    return beanDefinition;
  }
}
