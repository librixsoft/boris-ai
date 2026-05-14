package com.boris.librixsoft.server.service;

import com.boris.librixsoft.server.service.llama.BorisLLamaServerWrapper;
import org.springframework.aot.generate.Generated;
import org.springframework.beans.factory.aot.BeanInstanceSupplier;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.RootBeanDefinition;

/**
 * Bean definitions for {@link BorisServerService}.
 */
@Generated
public class BorisServerService__BeanDefinitions {
  /**
   * Get the bean instance supplier for 'borisServerService'.
   */
  private static BeanInstanceSupplier<BorisServerService> getBorisServerServiceInstanceSupplier() {
    return BeanInstanceSupplier.<BorisServerService>forConstructor(BorisLLamaServerWrapper.class, MultiModelOrchestratorService.class, ChatSessionService.class)
            .withGenerator((registeredBean, args) -> new BorisServerService(args.get(0), args.get(1), args.get(2)));
  }

  /**
   * Get the bean definition for 'borisServerService'.
   */
  public static BeanDefinition getBorisServerServiceBeanDefinition() {
    RootBeanDefinition beanDefinition = new RootBeanDefinition(BorisServerService.class);
    beanDefinition.setInstanceSupplier(getBorisServerServiceInstanceSupplier());
    return beanDefinition;
  }
}
