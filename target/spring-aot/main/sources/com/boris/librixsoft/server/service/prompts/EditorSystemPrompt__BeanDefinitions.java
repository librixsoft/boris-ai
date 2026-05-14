package com.boris.librixsoft.server.service.prompts;

import org.springframework.aot.generate.Generated;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.RootBeanDefinition;

/**
 * Bean definitions for {@link EditorSystemPrompt}.
 */
@Generated
public class EditorSystemPrompt__BeanDefinitions {
  /**
   * Get the bean definition for 'editorSystemPrompt'.
   */
  public static BeanDefinition getEditorSystemPromptBeanDefinition() {
    RootBeanDefinition beanDefinition = new RootBeanDefinition(EditorSystemPrompt.class);
    beanDefinition.setInstanceSupplier(EditorSystemPrompt::new);
    return beanDefinition;
  }
}
