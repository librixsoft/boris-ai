package com.boris.librixsoft.server.service;

import org.springframework.aot.generate.Generated;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.RootBeanDefinition;

/**
 * Bean definitions for {@link ChatSessionService}.
 */
@Generated
public class ChatSessionService__BeanDefinitions {
  /**
   * Get the bean definition for 'chatSessionService'.
   */
  public static BeanDefinition getChatSessionServiceBeanDefinition() {
    RootBeanDefinition beanDefinition = new RootBeanDefinition(ChatSessionService.class);
    beanDefinition.setInstanceSupplier(ChatSessionService::new);
    return beanDefinition;
  }
}
