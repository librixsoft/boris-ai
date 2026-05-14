package com.boris.librixsoft.server.controller;

import com.boris.librixsoft.server.service.llama.BorisLLamaServerWrapper;
import org.springframework.aot.generate.Generated;
import org.springframework.beans.factory.aot.BeanInstanceSupplier;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.RootBeanDefinition;

/**
 * Bean definitions for {@link ModelsController}.
 */
@Generated
public class ModelsController__BeanDefinitions {
  /**
   * Get the bean instance supplier for 'modelsController'.
   */
  private static BeanInstanceSupplier<ModelsController> getModelsControllerInstanceSupplier() {
    return BeanInstanceSupplier.<ModelsController>forConstructor(BorisLLamaServerWrapper.class)
            .withGenerator((registeredBean, args) -> new ModelsController(args.get(0)));
  }

  /**
   * Get the bean definition for 'modelsController'.
   */
  public static BeanDefinition getModelsControllerBeanDefinition() {
    RootBeanDefinition beanDefinition = new RootBeanDefinition(ModelsController.class);
    beanDefinition.setInstanceSupplier(getModelsControllerInstanceSupplier());
    return beanDefinition;
  }
}
