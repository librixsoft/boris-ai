package com.boris.librixsoft.server.service;

import org.springframework.aot.generate.Generated;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.RootBeanDefinition;

/**
 * Bean definitions for {@link HardwareService}.
 */
@Generated
public class HardwareService__BeanDefinitions {
  /**
   * Get the bean definition for 'hardwareService'.
   */
  public static BeanDefinition getHardwareServiceBeanDefinition() {
    RootBeanDefinition beanDefinition = new RootBeanDefinition(HardwareService.class);
    beanDefinition.setInstanceSupplier(HardwareService::new);
    return beanDefinition;
  }
}
