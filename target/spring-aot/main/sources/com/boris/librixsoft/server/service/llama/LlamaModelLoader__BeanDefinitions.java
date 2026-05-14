package com.boris.librixsoft.server.service.llama;

import com.boris.librixsoft.config.BorisProperties;
import org.springframework.aot.generate.Generated;
import org.springframework.beans.factory.aot.BeanInstanceSupplier;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.RootBeanDefinition;

/**
 * Bean definitions for {@link LlamaModelLoader}.
 */
@Generated
public class LlamaModelLoader__BeanDefinitions {
  /**
   * Get the bean instance supplier for 'llamaModelLoader'.
   */
  private static BeanInstanceSupplier<LlamaModelLoader> getLlamaModelLoaderInstanceSupplier() {
    return BeanInstanceSupplier.<LlamaModelLoader>forConstructor(BorisProperties.class)
            .withGenerator((registeredBean, args) -> new LlamaModelLoader(args.get(0)));
  }

  /**
   * Get the bean definition for 'llamaModelLoader'.
   */
  public static BeanDefinition getLlamaModelLoaderBeanDefinition() {
    RootBeanDefinition beanDefinition = new RootBeanDefinition(LlamaModelLoader.class);
    beanDefinition.setInstanceSupplier(getLlamaModelLoaderInstanceSupplier());
    return beanDefinition;
  }
}
