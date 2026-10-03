# Boris CLI

Asistente de linea de comandos en Java con colores y ASCII art.

## Landing Page Overview

The landing page serves as the entry point for users to understand what the Boris CLI offers, how to get started, and what value it brings through multi-agent collaboration. It provides an intuitive overview of the platform's capabilities, key features, and how the multi-agent integration enhances productivity.

### Landing Page Content Structure

1. **Welcome & Value Proposition**
   - Brief introduction to Boris CLI as a multi-agent CLI assistant
   - Key benefits: local AI, privacy, cost-free, full control
   - One-sentence summary of what it does

2. **Getting Started**
   - Simple installation instructions
   - Quick start examples
   - Basic usage scenarios

3. **Multi-Agent Collaboration**
   - Explanation of how multiple agents work together
   - Roles and responsibilities of each agent
   - Benefits of collaborative approach

4. **Core Features**
   - Key capabilities demonstrated through agents
   - How agents contribute to different tasks
   - Example workflows

5. **Getting Help & Support**
   - How to find help
   - Reporting issues
   - Community resources

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
   - Agent B adds unit tests, comprehensive error handling, and validation logic.
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
