package com.boris.librixsoft.server.controller;

import com.boris.librixsoft.server.service.BorisServerService;
import org.springframework.aot.generate.Generated;
import org.springframework.beans.factory.aot.BeanInstanceSupplier;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.RootBeanDefinition;

/**
 * Bean definitions for {@link BorisServerController}.
 */
@Generated
public class BorisServerController__BeanDefinitions {
  /**
   * Get the bean instance supplier for 'borisServerController'.
   */
  private static BeanInstanceSupplier<BorisServerController> getBorisServerControllerInstanceSupplier(
      ) {
    return BeanInstanceSupplier.<BorisServerController>forConstructor(BorisServerService.class)
            .withGenerator((registeredBean, args) -> new BorisServerController(args.get(0)));
  }

  /**
   * Get the bean definition for 'borisServerController'.
   */
  public static BeanDefinition getBorisServerControllerBeanDefinition() {
    RootBeanDefinition beanDefinition = new RootBeanDefinition(BorisServerController.class);
    beanDefinition.setInstanceSupplier(getBorisServerControllerInstanceSupplier());
    return beanDefinition;
  }
}
