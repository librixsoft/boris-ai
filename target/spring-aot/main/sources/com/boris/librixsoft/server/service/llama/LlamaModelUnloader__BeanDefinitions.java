package com.boris.librixsoft.server.service.llama;

import org.springframework.aot.generate.Generated;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.RootBeanDefinition;

/**
 * Bean definitions for {@link LlamaModelUnloader}.
 */
@Generated
public class LlamaModelUnloader__BeanDefinitions {
  /**
   * Get the bean definition for 'llamaModelUnloader'.
   */
  public static BeanDefinition getLlamaModelUnloaderBeanDefinition() {
    RootBeanDefinition beanDefinition = new RootBeanDefinition(LlamaModelUnloader.class);
    beanDefinition.setInstanceSupplier(LlamaModelUnloader::new);
    return beanDefinition;
  }
}
