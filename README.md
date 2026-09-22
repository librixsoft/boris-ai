# Boris CLI

Asistente de linea de comandos en Java con colores y ASCII art.

## Filosofía del proyecto

Este proyecto ha sido elaborado íntegramente con IA local.

La filosofía que lo guía es demostrar los alcances de la IA local e impulsar a los desarrolladores a preparar sus entornos de trabajo para desarrollos con IA local. Creemos que la IA local no es una alternativa de segunda clase a la nube, sino una vía con ventajas propias: privacidad total, cero dependencia de servicios externos, cero costo por uso y control absoluto sobre el modelo y los datos. Este proyecto es, en sí mismo, una prueba viva de que es posible construir software real, útil y funcional con una GPU propia y herramientas 100% locales.

El enfoque de este proyecto y de sus colaboraciones es exclusivo: solo líneas de código generadas con IA local a través de la GPU. Toda contribución debe seguir esa misma regla, de modo que cada línea escrita en este repositorio reafirme que la IA local está lista para el desarrollo serio y ayude a que más desarrolladores se animen a dar ese paso.

## Requisitos

- Java 21+
- Maven 3.6+

## Compilar

```bash
mvn clean package
```

## Ejecutar

```bash
java -jar target/boris-cli-1.0.0.jar
```

## Tests

```bash
# Ejecutar tests unitarios
mvn test

# Ejecutar tests e2e
mvn test -Pe2e

# O alternativamente
mvn integration-test
```

## Autor

Creado por **Anibal Gomez** — anibal@librixsoft.com

## Multi-Agent Integration

This section describes how the Boris CLI integrates multiple AI agents to provide a seamless collaborative environment for complex tasks.

### Overview

The multi-agent integration enables the CLI to leverage the strengths of multiple specialized AI agents working together on complex tasks that require diverse skills and perspectives. Each agent is designed to handle specific responsibilities while maintaining shared context and coordinating with others.

### Agent Roles and Responsibilities

1. **Worker_3 (Current Worker)** - The primary agent responsible for executing the current task, generating appropriate responses, and managing the workflow.

2. **Agent A** - A specialized agent focusing on domain-specific tasks such as content generation, analysis, or technical troubleshooting.

3. **Agent B** - Handles operational tasks including system monitoring, configuration management, and user interaction.

4. **Agent C** - Manages user experience, provides clear guidance, and maintains conversation flow with the user.

### Integration Mechanisms

- Shared Context Window: Maintains conversation state, relevant data, and task requirements across all agents.
- Structured Messaging: Agents communicate through well-defined messages containing task parameters, current state, and expected outcomes.
- Task Delegation: Complex tasks are broken into subtasks that can be handled by specialized agents based on their expertise.
- Feedback Loop: Continuous feedback allows agents to adjust their approach, improve accuracy, and maintain alignment with the overall goal.
- Robust Error Handling: Ensures the workflow remains functional even when individual agents encounter difficulties.
- Clear Separation of Concerns: Each agent focuses on specific responsibilities while maintaining consistent terminology and data formats.

### Implementation Architecture

The CLI uses a layered architecture where:
- The Core CLI manages command parsing and basic operations.
- The Multi-Agent Engine coordinates agent interactions, manages task distribution, and maintains global state.
- Each specialized agent utilizes its own processing pipeline and can incorporate specialized tools or models.
- The UI/UX layer presents results to the user while maintaining awareness of agent states and progress.

### Example Scenarios

1. **Content Creation Process**:
   - Agent A generates initial draft content based on requirements.
   - Agent B refines the content for tone, style, and clarity.
   - Agent C adds metadata, formatting, and presents the final result to the user with explanations.

2. **Technical Troubleshooting**:
   - Agent A identifies the root cause using error logs and diagnostic information.
   - Agent B provides diagnostic steps and potential solutions based on the findings.
   - Agent C explains the solution clearly to the user and implements it if needed.
   - The CLI updates the system configuration and confirms resolution.

3. **Code Generation**:
   - Agent A writes the main logic based on user requirements.
   - Agent B adds unit tests, validation logic, and comprehensive error handling.
   - Agent C documents the implementation, provides usage examples, and summarizes key points.
   - The generated code is packaged and ready for integration with clear instructions.

### Best Practices for Multi-Agent Collaboration

- Keep conversations focused and structured around clear objectives.
- Maintain consistent terminology and data formats across all agents.
- Log key interactions for auditing, improvement, and future reference.
- Implement timeouts and fallback procedures to ensure robustness.
- Allow agents to request clarification when ambiguity or incomplete information is detected.
- Encourage iterative refinement through feedback loops.

This design enables the CLI to handle complex, multi-step tasks efficiently while providing a smooth user experience that fully leverages the collaborative strengths of multiple AI agents.
