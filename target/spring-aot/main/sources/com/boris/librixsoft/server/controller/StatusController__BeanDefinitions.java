package com.boris.librixsoft.server.controller;

import com.boris.librixsoft.config.BorisProperties;
import com.boris.librixsoft.server.service.StatusEventService;
import com.boris.librixsoft.server.service.llama.BorisLLamaServerWrapper;
import org.springframework.aot.generate.Generated;
import org.springframework.beans.factory.aot.BeanInstanceSupplier;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.RootBeanDefinition;

/**
 * Bean definitions for {@link StatusController}.
 */
@Generated
public class StatusController__BeanDefinitions {
  /**
   * Get the bean instance supplier for 'statusController'.
   */
  private static BeanInstanceSupplier<StatusController> getStatusControllerInstanceSupplier() {
    return BeanInstanceSupplier.<StatusController>forConstructor(BorisProperties.class, BorisLLamaServerWrapper.class, StatusEventService.class)
            .withGenerator((registeredBean, args) -> new StatusController(args.get(0), args.get(1), args.get(2)));
  }

  /**
   * Get the bean definition for 'statusController'.
   */
  public static BeanDefinition getStatusControllerBeanDefinition() {
    RootBeanDefinition beanDefinition = new RootBeanDefinition(StatusController.class);
    beanDefinition.setInstanceSupplier(getStatusControllerInstanceSupplier());
    return beanDefinition;
  }
}
