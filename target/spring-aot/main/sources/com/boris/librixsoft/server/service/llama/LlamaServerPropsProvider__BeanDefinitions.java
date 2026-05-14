package com.boris.librixsoft.server.service.llama;

import org.springframework.aot.generate.Generated;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.RootBeanDefinition;

/**
 * Bean definitions for {@link LlamaServerPropsProvider}.
 */
@Generated
public class LlamaServerPropsProvider__BeanDefinitions {
  /**
   * Get the bean definition for 'llamaServerPropsProvider'.
   */
  public static BeanDefinition getLlamaServerPropsProviderBeanDefinition() {
    RootBeanDefinition beanDefinition = new RootBeanDefinition(LlamaServerPropsProvider.class);
    beanDefinition.setInstanceSupplier(LlamaServerPropsProvider::new);
    return beanDefinition;
  }
}
