package com.boris.librixsoft.server.service.llama;

import com.boris.librixsoft.config.BorisProperties;
import org.springframework.aot.generate.Generated;
import org.springframework.beans.factory.aot.BeanInstanceSupplier;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.RootBeanDefinition;

/**
 * Bean definitions for {@link LlamaModelLister}.
 */
@Generated
public class LlamaModelLister__BeanDefinitions {
  /**
   * Get the bean instance supplier for 'llamaModelLister'.
   */
  private static BeanInstanceSupplier<LlamaModelLister> getLlamaModelListerInstanceSupplier() {
    return BeanInstanceSupplier.<LlamaModelLister>forConstructor(BorisProperties.class)
            .withGenerator((registeredBean, args) -> new LlamaModelLister(args.get(0)));
  }

  /**
   * Get the bean definition for 'llamaModelLister'.
   */
  public static BeanDefinition getLlamaModelListerBeanDefinition() {
    RootBeanDefinition beanDefinition = new RootBeanDefinition(LlamaModelLister.class);
    beanDefinition.setInstanceSupplier(getLlamaModelListerInstanceSupplier());
    return beanDefinition;
  }
}
