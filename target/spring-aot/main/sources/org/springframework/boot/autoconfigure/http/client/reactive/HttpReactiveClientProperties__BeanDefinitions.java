package org.springframework.boot.autoconfigure.http.client.reactive;

import org.springframework.aot.generate.Generated;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.RootBeanDefinition;

/**
 * Bean definitions for {@link HttpReactiveClientProperties}.
 */
@Generated
public class HttpReactiveClientProperties__BeanDefinitions {
  /**
   * Get the bean definition for 'httpReactiveClientProperties'.
   */
  public static BeanDefinition getHttpReactiveClientPropertiesBeanDefinition() {
    RootBeanDefinition beanDefinition = new RootBeanDefinition(HttpReactiveClientProperties.class);
    beanDefinition.setInstanceSupplier(HttpReactiveClientProperties::new);
    return beanDefinition;
  }
}
