package com.boris.librixsoft.agent.tools;

import org.springframework.aot.generate.Generated;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.InstanceSupplier;
import org.springframework.beans.factory.support.RootBeanDefinition;

/**
 * Bean definitions for {@link ReadFileTool}.
 */
@Generated
public class ReadFileTool__BeanDefinitions {
  /**
   * Get the bean definition for 'readFileTool'.
   */
  public static BeanDefinition getReadFileToolBeanDefinition() {
    RootBeanDefinition beanDefinition = new RootBeanDefinition(ReadFileTool.class);
    InstanceSupplier<ReadFileTool> instanceSupplier = InstanceSupplier.using(ReadFileTool::new);
    instanceSupplier = instanceSupplier.andThen(ReadFileTool__Autowiring::apply);
    beanDefinition.setInstanceSupplier(instanceSupplier);
    return beanDefinition;
  }
}
