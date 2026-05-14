package com.boris.librixsoft.server.service.llama;

import org.springframework.aot.generate.Generated;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.RootBeanDefinition;

/**
 * Bean definitions for {@link JnaLlamaChatModel}.
 */
@Generated
public class JnaLlamaChatModel__BeanDefinitions {
  /**
   * Get the bean definition for 'jnaLlamaChatModel'.
   */
  public static BeanDefinition getJnaLlamaChatModelBeanDefinition() {
    RootBeanDefinition beanDefinition = new RootBeanDefinition(JnaLlamaChatModel.class);
    beanDefinition.setInstanceSupplier(JnaLlamaChatModel::new);
    return beanDefinition;
  }
}
