package com.boris.librixsoft.server.service;

import com.boris.librixsoft.agent.tools.CreateFileTool;
import com.boris.librixsoft.agent.tools.DeleteFileTool;
import com.boris.librixsoft.agent.tools.DeleteFolderTool;
import com.boris.librixsoft.agent.tools.EditFileTool;
import com.boris.librixsoft.agent.tools.EditFileWithReplacementTool;
import com.boris.librixsoft.agent.tools.ReadFileTool;
import com.boris.librixsoft.agent.tools.RunCommandTool;
import com.boris.librixsoft.config.BorisProperties;
import com.boris.librixsoft.server.service.llama.BorisLLamaServerWrapper;
import com.boris.librixsoft.server.service.llama.JnaLlamaChatModel;
import org.springframework.aot.generate.Generated;
import org.springframework.beans.factory.aot.BeanInstanceSupplier;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.RootBeanDefinition;

/**
 * Bean definitions for {@link LlamaChatService}.
 */
@Generated
public class LlamaChatService__BeanDefinitions {
  /**
   * Get the bean instance supplier for 'llamaChatService'.
   */
  private static BeanInstanceSupplier<LlamaChatService> getLlamaChatServiceInstanceSupplier() {
    return BeanInstanceSupplier.<LlamaChatService>forConstructor(JnaLlamaChatModel.class, BorisProperties.class, BorisLLamaServerWrapper.class, CreateFileTool.class, ReadFileTool.class, EditFileTool.class, EditFileWithReplacementTool.class, DeleteFileTool.class, DeleteFolderTool.class, RunCommandTool.class)
            .withGenerator((registeredBean, args) -> new LlamaChatService(args.get(0), args.get(1), args.get(2), args.get(3), args.get(4), args.get(5), args.get(6), args.get(7), args.get(8), args.get(9)));
  }

  /**
   * Get the bean definition for 'llamaChatService'.
   */
  public static BeanDefinition getLlamaChatServiceBeanDefinition() {
    RootBeanDefinition beanDefinition = new RootBeanDefinition(LlamaChatService.class);
    beanDefinition.setInstanceSupplier(getLlamaChatServiceInstanceSupplier());
    return beanDefinition;
  }
}
