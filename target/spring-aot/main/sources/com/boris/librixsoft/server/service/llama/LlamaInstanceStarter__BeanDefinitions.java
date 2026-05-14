package com.boris.librixsoft.server.service.llama;

import com.boris.librixsoft.config.BorisProperties;
import com.boris.librixsoft.server.service.LlamaServerDownloadService;
import org.springframework.aot.generate.Generated;
import org.springframework.beans.factory.aot.BeanInstanceSupplier;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.RootBeanDefinition;

/**
 * Bean definitions for {@link LlamaInstanceStarter}.
 */
@Generated
public class LlamaInstanceStarter__BeanDefinitions {
  /**
   * Get the bean instance supplier for 'llamaInstanceStarter'.
   */
  private static BeanInstanceSupplier<LlamaInstanceStarter> getLlamaInstanceStarterInstanceSupplier(
      ) {
    return BeanInstanceSupplier.<LlamaInstanceStarter>forConstructor(BorisProperties.class, LlamaServerDownloadService.class)
            .withGenerator((registeredBean, args) -> new LlamaInstanceStarter(args.get(0), args.get(1)));
  }

  /**
   * Get the bean definition for 'llamaInstanceStarter'.
   */
  public static BeanDefinition getLlamaInstanceStarterBeanDefinition() {
    RootBeanDefinition beanDefinition = new RootBeanDefinition(LlamaInstanceStarter.class);
    beanDefinition.setInstanceSupplier(getLlamaInstanceStarterInstanceSupplier());
    return beanDefinition;
  }
}
