package com.boris.librixsoft;

import org.springframework.aot.generate.Generated;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.RootBeanDefinition;

/**
 * Bean definitions for {@link BorisServerApplication}.
 */
@Generated
public class BorisServerApplication__BeanDefinitions {
  /**
   * Get the bean definition for 'borisServerApplication'.
   */
  public static BeanDefinition getBorisServerApplicationBeanDefinition() {
    RootBeanDefinition beanDefinition = new RootBeanDefinition(BorisServerApplication.class);
    beanDefinition.setInstanceSupplier(BorisServerApplication::new);
    return beanDefinition;
  }
}
