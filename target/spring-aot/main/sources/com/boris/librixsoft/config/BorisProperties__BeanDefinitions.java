package com.boris.librixsoft.config;

import org.springframework.aot.generate.Generated;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.InstanceSupplier;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.context.annotation.ConfigurationClassUtils;

/**
 * Bean definitions for {@link BorisProperties}.
 */
@Generated
public class BorisProperties__BeanDefinitions {
  /**
   * Get the bean definition for 'borisProperties'.
   */
  public static BeanDefinition getBorisPropertiesBeanDefinition() {
    RootBeanDefinition beanDefinition = new RootBeanDefinition(BorisProperties.class);
    beanDefinition.setTargetType(BorisProperties.class);
    ConfigurationClassUtils.initializeConfigurationClass(BorisProperties.class);
    InstanceSupplier<BorisProperties> instanceSupplier = InstanceSupplier.using(BorisProperties$$SpringCGLIB$$0::new);
    instanceSupplier = instanceSupplier.andThen(BorisProperties__Autowiring::apply);
    beanDefinition.setInstanceSupplier(instanceSupplier);
    return beanDefinition;
  }
}
