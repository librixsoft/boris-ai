package com.boris.librixsoft.agent.tools;

import org.springframework.aot.generate.Generated;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.InstanceSupplier;
import org.springframework.beans.factory.support.RootBeanDefinition;

/**
 * Bean definitions for {@link EditFileWithReplacementTool}.
 */
@Generated
public class EditFileWithReplacementTool__BeanDefinitions {
  /**
   * Get the bean definition for 'editFileWithReplacementTool'.
   */
  public static BeanDefinition getEditFileWithReplacementToolBeanDefinition() {
    RootBeanDefinition beanDefinition = new RootBeanDefinition(EditFileWithReplacementTool.class);
    InstanceSupplier<EditFileWithReplacementTool> instanceSupplier = InstanceSupplier.using(EditFileWithReplacementTool::new);
    instanceSupplier = instanceSupplier.andThen(EditFileWithReplacementTool__Autowiring::apply);
    beanDefinition.setInstanceSupplier(instanceSupplier);
    return beanDefinition;
  }
}
