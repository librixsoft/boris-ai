package com.boris.librixsoft.agent.tools;

import org.springframework.aot.generate.Generated;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.InstanceSupplier;
import org.springframework.beans.factory.support.RootBeanDefinition;

/**
 * Bean definitions for {@link DeleteFolderTool}.
 */
@Generated
public class DeleteFolderTool__BeanDefinitions {
  /**
   * Get the bean definition for 'deleteFolderTool'.
   */
  public static BeanDefinition getDeleteFolderToolBeanDefinition() {
    RootBeanDefinition beanDefinition = new RootBeanDefinition(DeleteFolderTool.class);
    InstanceSupplier<DeleteFolderTool> instanceSupplier = InstanceSupplier.using(DeleteFolderTool::new);
    instanceSupplier = instanceSupplier.andThen(DeleteFolderTool__Autowiring::apply);
    beanDefinition.setInstanceSupplier(instanceSupplier);
    return beanDefinition;
  }
}
