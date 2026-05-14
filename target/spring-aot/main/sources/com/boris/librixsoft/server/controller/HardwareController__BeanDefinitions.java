package com.boris.librixsoft.server.controller;

import com.boris.librixsoft.server.service.HardwareService;
import com.boris.librixsoft.server.service.llama.BorisLLamaServerWrapper;
import org.springframework.aot.generate.Generated;
import org.springframework.beans.factory.aot.BeanInstanceSupplier;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.RootBeanDefinition;

/**
 * Bean definitions for {@link HardwareController}.
 */
@Generated
public class HardwareController__BeanDefinitions {
  /**
   * Get the bean instance supplier for 'hardwareController'.
   */
  private static BeanInstanceSupplier<HardwareController> getHardwareControllerInstanceSupplier() {
    return BeanInstanceSupplier.<HardwareController>forConstructor(HardwareService.class, BorisLLamaServerWrapper.class)
            .withGenerator((registeredBean, args) -> new HardwareController(args.get(0), args.get(1)));
  }

  /**
   * Get the bean definition for 'hardwareController'.
   */
  public static BeanDefinition getHardwareControllerBeanDefinition() {
    RootBeanDefinition beanDefinition = new RootBeanDefinition(HardwareController.class);
    beanDefinition.setInstanceSupplier(getHardwareControllerInstanceSupplier());
    return beanDefinition;
  }
}
