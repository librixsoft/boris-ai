package org.springframework.boot.autoconfigure.http.client.reactive;

import org.springframework.aot.generate.Generated;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.aot.BeanInstanceSupplier;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.boot.http.client.reactive.ClientHttpConnectorBuilder;
import org.springframework.boot.http.client.reactive.ClientHttpConnectorSettings;
import org.springframework.core.ResolvableType;
import org.springframework.http.client.reactive.ClientHttpConnector;

/**
 * Bean definitions for {@link ClientHttpConnectorAutoConfiguration}.
 */
@Generated
public class ClientHttpConnectorAutoConfiguration__BeanDefinitions {
  /**
   * Get the bean instance supplier for 'org.springframework.boot.autoconfigure.http.client.reactive.ClientHttpConnectorAutoConfiguration'.
   */
  private static BeanInstanceSupplier<ClientHttpConnectorAutoConfiguration> getClientHttpConnectorAutoConfigurationInstanceSupplier(
      ) {
    return BeanInstanceSupplier.<ClientHttpConnectorAutoConfiguration>forConstructor(ObjectProvider.class, HttpReactiveClientProperties.class)
            .withGenerator((registeredBean, args) -> new ClientHttpConnectorAutoConfiguration(args.get(0), args.get(1)));
  }

  /**
   * Get the bean definition for 'clientHttpConnectorAutoConfiguration'.
   */
  public static BeanDefinition getClientHttpConnectorAutoConfigurationBeanDefinition() {
    RootBeanDefinition beanDefinition = new RootBeanDefinition(ClientHttpConnectorAutoConfiguration.class);
    beanDefinition.setInstanceSupplier(getClientHttpConnectorAutoConfigurationInstanceSupplier());
    return beanDefinition;
  }

  /**
   * Get the bean instance supplier for 'clientHttpConnectorBuilder'.
   */
  private static BeanInstanceSupplier<ClientHttpConnectorBuilder> getClientHttpConnectorBuilderInstanceSupplier(
      ) {
    return BeanInstanceSupplier.<ClientHttpConnectorBuilder>forFactoryMethod(ClientHttpConnectorAutoConfiguration.class, "clientHttpConnectorBuilder", ObjectProvider.class)
            .withGenerator((registeredBean, args) -> registeredBean.getBeanFactory().getBean("org.springframework.boot.autoconfigure.http.client.reactive.ClientHttpConnectorAutoConfiguration", ClientHttpConnectorAutoConfiguration.class).clientHttpConnectorBuilder(args.get(0)));
  }

  /**
   * Get the bean definition for 'clientHttpConnectorBuilder'.
   */
  public static BeanDefinition getClientHttpConnectorBuilderBeanDefinition() {
    RootBeanDefinition beanDefinition = new RootBeanDefinition(ClientHttpConnectorBuilder.class);
    beanDefinition.setTargetType(ResolvableType.forClass(ClientHttpConnectorBuilder.class));
    beanDefinition.setFactoryBeanName("org.springframework.boot.autoconfigure.http.client.reactive.ClientHttpConnectorAutoConfiguration");
    beanDefinition.setInstanceSupplier(getClientHttpConnectorBuilderInstanceSupplier());
    return beanDefinition;
  }

  /**
   * Get the bean instance supplier for 'clientHttpConnectorSettings'.
   */
  private static BeanInstanceSupplier<ClientHttpConnectorSettings> getClientHttpConnectorSettingsInstanceSupplier(
      ) {
    return BeanInstanceSupplier.<ClientHttpConnectorSettings>forFactoryMethod(ClientHttpConnectorAutoConfiguration.class, "clientHttpConnectorSettings")
            .withGenerator((registeredBean) -> registeredBean.getBeanFactory().getBean("org.springframework.boot.autoconfigure.http.client.reactive.ClientHttpConnectorAutoConfiguration", ClientHttpConnectorAutoConfiguration.class).clientHttpConnectorSettings());
  }

  /**
   * Get the bean definition for 'clientHttpConnectorSettings'.
   */
  public static BeanDefinition getClientHttpConnectorSettingsBeanDefinition() {
    RootBeanDefinition beanDefinition = new RootBeanDefinition(ClientHttpConnectorSettings.class);
    beanDefinition.setFactoryBeanName("org.springframework.boot.autoconfigure.http.client.reactive.ClientHttpConnectorAutoConfiguration");
    beanDefinition.setInstanceSupplier(getClientHttpConnectorSettingsInstanceSupplier());
    return beanDefinition;
  }

  /**
   * Get the bean instance supplier for 'clientHttpConnector'.
   */
  private static BeanInstanceSupplier<ClientHttpConnector> getClientHttpConnectorInstanceSupplier(
      ) {
    return BeanInstanceSupplier.<ClientHttpConnector>forFactoryMethod(ClientHttpConnectorAutoConfiguration.class, "clientHttpConnector", ClientHttpConnectorBuilder.class, ClientHttpConnectorSettings.class)
            .withGenerator((registeredBean, args) -> registeredBean.getBeanFactory().getBean("org.springframework.boot.autoconfigure.http.client.reactive.ClientHttpConnectorAutoConfiguration", ClientHttpConnectorAutoConfiguration.class).clientHttpConnector(args.get(0), args.get(1)));
  }

  /**
   * Get the bean definition for 'clientHttpConnector'.
   */
  public static BeanDefinition getClientHttpConnectorBeanDefinition() {
    RootBeanDefinition beanDefinition = new RootBeanDefinition(ClientHttpConnector.class);
    beanDefinition.setLazyInit(true);
    beanDefinition.setFactoryBeanName("org.springframework.boot.autoconfigure.http.client.reactive.ClientHttpConnectorAutoConfiguration");
    beanDefinition.setInstanceSupplier(getClientHttpConnectorInstanceSupplier());
    return beanDefinition;
  }
}
