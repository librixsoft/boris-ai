package com.boris.librixsoft.client.controller;

import org.springframework.aot.generate.Generated;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.RootBeanDefinition;

/**
 * Bean definitions for {@link BorisUIController}.
 */
@Generated
public class BorisUIController__BeanDefinitions {
  /**
   * Get the bean definition for 'borisUIController'.
   */
  public static BeanDefinition getBorisUIControllerBeanDefinition() {
    RootBeanDefinition beanDefinition = new RootBeanDefinition(BorisUIController.class);
    beanDefinition.setInstanceSupplier(BorisUIController::new);
    return beanDefinition;
  }
}
