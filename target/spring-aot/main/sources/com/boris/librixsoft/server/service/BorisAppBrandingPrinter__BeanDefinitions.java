package com.boris.librixsoft.server.service;

import com.boris.librixsoft.config.BorisProperties;
import org.springframework.aot.generate.Generated;
import org.springframework.beans.factory.aot.BeanInstanceSupplier;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.RootBeanDefinition;

/**
 * Bean definitions for {@link BorisAppBrandingPrinter}.
 */
@Generated
public class BorisAppBrandingPrinter__BeanDefinitions {
  /**
   * Get the bean instance supplier for 'borisAppBrandingPrinter'.
   */
  private static BeanInstanceSupplier<BorisAppBrandingPrinter> getBorisAppBrandingPrinterInstanceSupplier(
      ) {
    return BeanInstanceSupplier.<BorisAppBrandingPrinter>forConstructor(BorisProperties.class)
            .withGenerator((registeredBean, args) -> new BorisAppBrandingPrinter(args.get(0)));
  }

  /**
   * Get the bean definition for 'borisAppBrandingPrinter'.
   */
  public static BeanDefinition getBorisAppBrandingPrinterBeanDefinition() {
    RootBeanDefinition beanDefinition = new RootBeanDefinition(BorisAppBrandingPrinter.class);
    beanDefinition.setInstanceSupplier(getBorisAppBrandingPrinterInstanceSupplier());
    return beanDefinition;
  }
}
