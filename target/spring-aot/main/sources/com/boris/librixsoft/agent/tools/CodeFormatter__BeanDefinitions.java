package com.boris.librixsoft.agent.tools;

import org.springframework.aot.generate.Generated;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.RootBeanDefinition;

/**
 * Bean definitions for {@link CodeFormatter}.
 */
@Generated
public class CodeFormatter__BeanDefinitions {
  /**
   * Get the bean definition for 'codeFormatter'.
   */
  public static BeanDefinition getCodeFormatterBeanDefinition() {
    RootBeanDefinition beanDefinition = new RootBeanDefinition(CodeFormatter.class);
    beanDefinition.setInstanceSupplier(CodeFormatter::new);
    return beanDefinition;
  }
}
