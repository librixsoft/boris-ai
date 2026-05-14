package com.boris.librixsoft.server.service;

import com.boris.librixsoft.config.BorisProperties;
import org.springframework.aot.generate.Generated;
import org.springframework.beans.factory.aot.BeanInstanceSupplier;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.RootBeanDefinition;

/**
 * Bean definitions for {@link LlamaServerDownloadService}.
 */
@Generated
public class LlamaServerDownloadService__BeanDefinitions {
  /**
   * Get the bean instance supplier for 'llamaServerDownloadService'.
   */
  private static BeanInstanceSupplier<LlamaServerDownloadService> getLlamaServerDownloadServiceInstanceSupplier(
      ) {
    return BeanInstanceSupplier.<LlamaServerDownloadService>forConstructor(BorisProperties.class, HardwareService.class)
            .withGenerator((registeredBean, args) -> new LlamaServerDownloadService(args.get(0), args.get(1)));
  }

  /**
   * Get the bean definition for 'llamaServerDownloadService'.
   */
  public static BeanDefinition getLlamaServerDownloadServiceBeanDefinition() {
    RootBeanDefinition beanDefinition = new RootBeanDefinition(LlamaServerDownloadService.class);
    beanDefinition.setInstanceSupplier(getLlamaServerDownloadServiceInstanceSupplier());
    return beanDefinition;
  }
}
