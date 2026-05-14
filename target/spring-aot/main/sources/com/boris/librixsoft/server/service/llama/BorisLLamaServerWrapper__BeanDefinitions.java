package com.boris.librixsoft.server.service.llama;

import com.boris.librixsoft.config.BorisProperties;
import com.boris.librixsoft.server.service.BorisAppBrandingPrinter;
import com.boris.librixsoft.server.service.LlamaServerDownloadService;
import org.springframework.aot.generate.Generated;
import org.springframework.beans.factory.aot.BeanInstanceSupplier;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.RootBeanDefinition;

/**
 * Bean definitions for {@link BorisLLamaServerWrapper}.
 */
@Generated
public class BorisLLamaServerWrapper__BeanDefinitions {
  /**
   * Get the bean instance supplier for 'borisLLamaServerWrapper'.
   */
  private static BeanInstanceSupplier<BorisLLamaServerWrapper> getBorisLLamaServerWrapperInstanceSupplier(
      ) {
    return BeanInstanceSupplier.<BorisLLamaServerWrapper>forConstructor(BorisProperties.class, BorisAppBrandingPrinter.class, LlamaServerDownloadService.class, LlamaInstanceStarter.class, LlamaModelLoader.class, LlamaModelUnloader.class, LlamaModelClearer.class, LlamaModelLister.class, LlamaServerPropsProvider.class, LlamaModelSizeProvider.class, LlamaModelConfigResolver.class, JnaLlamaChatModel.class)
            .withGenerator((registeredBean, args) -> new BorisLLamaServerWrapper(args.get(0), args.get(1), args.get(2), args.get(3), args.get(4), args.get(5), args.get(6), args.get(7), args.get(8), args.get(9), args.get(10), args.get(11)));
  }

  /**
   * Get the bean definition for 'borisLLamaServerWrapper'.
   */
  public static BeanDefinition getBorisLLamaServerWrapperBeanDefinition() {
    RootBeanDefinition beanDefinition = new RootBeanDefinition(BorisLLamaServerWrapper.class);
    beanDefinition.setInitMethodNames("init");
    beanDefinition.setDestroyMethodNames("stopServer");
    beanDefinition.setInstanceSupplier(getBorisLLamaServerWrapperInstanceSupplier());
    return beanDefinition;
  }
}
