package com.boris.librixsoft.server.controller;

import com.boris.librixsoft.server.service.LlamaServerDownloadService;
import com.boris.librixsoft.server.service.llama.BorisLLamaServerWrapper;
import org.springframework.aot.generate.Generated;
import org.springframework.beans.factory.aot.BeanInstanceSupplier;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.RootBeanDefinition;

/**
 * Bean definitions for {@link LlamaServerDownloadController}.
 */
@Generated
public class LlamaServerDownloadController__BeanDefinitions {
  /**
   * Get the bean instance supplier for 'llamaServerDownloadController'.
   */
  private static BeanInstanceSupplier<LlamaServerDownloadController> getLlamaServerDownloadControllerInstanceSupplier(
      ) {
    return BeanInstanceSupplier.<LlamaServerDownloadController>forConstructor(LlamaServerDownloadService.class, BorisLLamaServerWrapper.class)
            .withGenerator((registeredBean, args) -> new LlamaServerDownloadController(args.get(0), args.get(1)));
  }

  /**
   * Get the bean definition for 'llamaServerDownloadController'.
   */
  public static BeanDefinition getLlamaServerDownloadControllerBeanDefinition() {
    RootBeanDefinition beanDefinition = new RootBeanDefinition(LlamaServerDownloadController.class);
    beanDefinition.setInstanceSupplier(getLlamaServerDownloadControllerInstanceSupplier());
    return beanDefinition;
  }
}
