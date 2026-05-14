package com.boris.librixsoft.server.service;

import org.springframework.aot.generate.Generated;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.RootBeanDefinition;

/**
 * Bean definitions for {@link StatusEventService}.
 */
@Generated
public class StatusEventService__BeanDefinitions {
  /**
   * Get the bean definition for 'statusEventService'.
   */
  public static BeanDefinition getStatusEventServiceBeanDefinition() {
    RootBeanDefinition beanDefinition = new RootBeanDefinition(StatusEventService.class);
    beanDefinition.setInitMethodNames("init");
    beanDefinition.setDestroyMethodNames("shutdown");
    beanDefinition.setInstanceSupplier(StatusEventService::new);
    return beanDefinition;
  }
}
