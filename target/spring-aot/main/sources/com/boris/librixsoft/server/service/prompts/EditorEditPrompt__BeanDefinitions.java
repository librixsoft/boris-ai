package com.boris.librixsoft.server.service.prompts;

import org.springframework.aot.generate.Generated;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.RootBeanDefinition;

/**
 * Bean definitions for {@link EditorEditPrompt}.
 */
@Generated
public class EditorEditPrompt__BeanDefinitions {
  /**
   * Get the bean definition for 'editorEditPrompt'.
   */
  public static BeanDefinition getEditorEditPromptBeanDefinition() {
    RootBeanDefinition beanDefinition = new RootBeanDefinition(EditorEditPrompt.class);
    beanDefinition.setInstanceSupplier(EditorEditPrompt::new);
    return beanDefinition;
  }
}
