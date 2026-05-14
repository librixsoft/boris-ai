package com.boris.librixsoft.server.service.llama;

import com.boris.librixsoft.config.BorisProperties;
import org.springframework.aot.generate.Generated;
import org.springframework.beans.factory.aot.BeanInstanceSupplier;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.RootBeanDefinition;

/**
 * Bean definitions for {@link LlamaModelSizeProvider}.
 */
@Generated
public class LlamaModelSizeProvider__BeanDefinitions {
  /**
   * Get the bean instance supplier for 'llamaModelSizeProvider'.
   */
  private static BeanInstanceSupplier<LlamaModelSizeProvider> getLlamaModelSizeProviderInstanceSupplier(
      ) {
    return BeanInstanceSupplier.<LlamaModelSizeProvider>forConstructor(BorisProperties.class)
            .withGenerator((registeredBean, args) -> new LlamaModelSizeProvider(args.get(0)));
  }

  /**
   * Get the bean definition for 'llamaModelSizeProvider'.
   */
  public static BeanDefinition getLlamaModelSizeProviderBeanDefinition() {
    RootBeanDefinition beanDefinition = new RootBeanDefinition(LlamaModelSizeProvider.class);
    beanDefinition.setInstanceSupplier(getLlamaModelSizeProviderInstanceSupplier());
    return beanDefinition;
  }
}
