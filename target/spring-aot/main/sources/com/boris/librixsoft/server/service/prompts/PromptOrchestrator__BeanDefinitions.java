package com.boris.librixsoft.server.service.prompts;

import org.springframework.aot.generate.Generated;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.RootBeanDefinition;

/**
 * Bean definitions for {@link PromptOrchestrator}.
 */
@Generated
public class PromptOrchestrator__BeanDefinitions {
  /**
   * Get the bean definition for 'promptOrchestrator'.
   */
  public static BeanDefinition getPromptOrchestratorBeanDefinition() {
    RootBeanDefinition beanDefinition = new RootBeanDefinition(PromptOrchestrator.class);
    beanDefinition.setInstanceSupplier(PromptOrchestrator::new);
    return beanDefinition;
  }
}
