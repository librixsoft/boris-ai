package com.boris.librixsoft.server.service;

import com.boris.librixsoft.agent.tools.ReadFileTool;
import com.boris.librixsoft.config.BorisProperties;
import com.boris.librixsoft.server.service.prompts.EditorEditPrompt;
import com.boris.librixsoft.server.service.prompts.EditorSystemPrompt;
import com.boris.librixsoft.server.service.prompts.PromptOrchestrator;
import org.springframework.aot.generate.Generated;
import org.springframework.beans.factory.aot.BeanInstanceSupplier;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.RootBeanDefinition;

/**
 * Bean definitions for {@link MultiModelOrchestratorService}.
 */
@Generated
public class MultiModelOrchestratorService__BeanDefinitions {
  /**
   * Get the bean instance supplier for 'multiModelOrchestratorService'.
   */
  private static BeanInstanceSupplier<MultiModelOrchestratorService> getMultiModelOrchestratorServiceInstanceSupplier(
      ) {
    return BeanInstanceSupplier.<MultiModelOrchestratorService>forConstructor(BorisProperties.class, LlamaChatService.class, PromptOrchestrator.class, EditorSystemPrompt.class, EditorEditPrompt.class, ReadFileTool.class, ChatSessionService.class)
            .withGenerator((registeredBean, args) -> new MultiModelOrchestratorService(args.get(0), args.get(1), args.get(2), args.get(3), args.get(4), args.get(5), args.get(6)));
  }

  /**
   * Get the bean definition for 'multiModelOrchestratorService'.
   */
  public static BeanDefinition getMultiModelOrchestratorServiceBeanDefinition() {
    RootBeanDefinition beanDefinition = new RootBeanDefinition(MultiModelOrchestratorService.class);
    beanDefinition.setInstanceSupplier(getMultiModelOrchestratorServiceInstanceSupplier());
    return beanDefinition;
  }
}
