package com.boris.librixsoft.server.service.llama;

import org.springframework.aot.generate.Generated;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.RootBeanDefinition;

/**
 * Bean definitions for {@link LlamaModelClearer}.
 */
@Generated
public class LlamaModelClearer__BeanDefinitions {
  /**
   * Get the bean definition for 'llamaModelClearer'.
   */
  public static BeanDefinition getLlamaModelClearerBeanDefinition() {
    RootBeanDefinition beanDefinition = new RootBeanDefinition(LlamaModelClearer.class);
    beanDefinition.setInstanceSupplier(LlamaModelClearer::new);
    return beanDefinition;
  }
}
