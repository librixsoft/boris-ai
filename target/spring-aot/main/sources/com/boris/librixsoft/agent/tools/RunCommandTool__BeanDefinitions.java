package com.boris.librixsoft.agent.tools;

import org.springframework.aot.generate.Generated;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.InstanceSupplier;
import org.springframework.beans.factory.support.RootBeanDefinition;

/**
 * Bean definitions for {@link RunCommandTool}.
 */
@Generated
public class RunCommandTool__BeanDefinitions {
  /**
   * Get the bean definition for 'runCommandTool'.
   */
  public static BeanDefinition getRunCommandToolBeanDefinition() {
    RootBeanDefinition beanDefinition = new RootBeanDefinition(RunCommandTool.class);
    InstanceSupplier<RunCommandTool> instanceSupplier = InstanceSupplier.using(RunCommandTool::new);
    instanceSupplier = instanceSupplier.andThen(RunCommandTool__Autowiring::apply);
    beanDefinition.setInstanceSupplier(instanceSupplier);
    return beanDefinition;
  }
}
