package com.boris.librixsoft.server.service;

import org.springframework.aot.generate.Generated;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.RootBeanDefinition;

/**
 * Bean definitions for {@link TaskParserService}.
 */
@Generated
public class TaskParserService__BeanDefinitions {
  /**
   * Get the bean definition for 'taskParserService'.
   */
  public static BeanDefinition getTaskParserServiceBeanDefinition() {
    RootBeanDefinition beanDefinition = new RootBeanDefinition(TaskParserService.class);
    beanDefinition.setInstanceSupplier(TaskParserService::new);
    return beanDefinition;
  }
}
