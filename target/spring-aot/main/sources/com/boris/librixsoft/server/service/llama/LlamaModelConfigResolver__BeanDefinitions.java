package com.boris.librixsoft.server.service.llama;

import com.boris.librixsoft.config.BorisProperties;
import org.springframework.aot.generate.Generated;
import org.springframework.beans.factory.aot.BeanInstanceSupplier;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.RootBeanDefinition;

/**
 * Bean definitions for {@link LlamaModelConfigResolver}.
 */
@Generated
public class LlamaModelConfigResolver__BeanDefinitions {
  /**
   * Get the bean instance supplier for 'llamaModelConfigResolver'.
   */
  private static BeanInstanceSupplier<LlamaModelConfigResolver> getLlamaModelConfigResolverInstanceSupplier(
      ) {
    return BeanInstanceSupplier.<LlamaModelConfigResolver>forConstructor(BorisProperties.class)
            .withGenerator((registeredBean, args) -> new LlamaModelConfigResolver(args.get(0)));
  }

  /**
   * Get the bean definition for 'llamaModelConfigResolver'.
   */
  public static BeanDefinition getLlamaModelConfigResolverBeanDefinition() {
    RootBeanDefinition beanDefinition = new RootBeanDefinition(LlamaModelConfigResolver.class);
    beanDefinition.setInstanceSupplier(getLlamaModelConfigResolverInstanceSupplier());
    return beanDefinition;
  }
}
