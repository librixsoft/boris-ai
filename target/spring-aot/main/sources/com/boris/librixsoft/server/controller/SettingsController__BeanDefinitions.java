package com.boris.librixsoft.server.controller;

import com.boris.librixsoft.config.BorisProperties;
import org.springframework.aot.generate.Generated;
import org.springframework.beans.factory.aot.BeanInstanceSupplier;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.RootBeanDefinition;

/**
 * Bean definitions for {@link SettingsController}.
 */
@Generated
public class SettingsController__BeanDefinitions {
  /**
   * Get the bean instance supplier for 'settingsController'.
   */
  private static BeanInstanceSupplier<SettingsController> getSetTingsControllerInstanceSupplier() {
    return BeanInstanceSupplier.<SettingsController>forConstructor(BorisProperties.class)
            .withGenerator((registeredBean, args) -> new SettingsController(args.get(0)));
  }

  /**
   * Get the bean definition for 'settingsController'.
   */
  public static BeanDefinition getSetTingsControllerBeanDefinition() {
    RootBeanDefinition beanDefinition = new RootBeanDefinition(SettingsController.class);
    beanDefinition.setInstanceSupplier(getSetTingsControllerInstanceSupplier());
    return beanDefinition;
  }
}
