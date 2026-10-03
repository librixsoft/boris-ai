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

### Con run.sh (recomendado)

```bash
./run.sh
```

Opciones:
- `./run.sh` — compila sin tests y ejecuta la aplicación.
- `./run.sh --run-tests` — compila con tests y ejecuta la aplicación.
- `./run.sh -- run.sh --run-tests` — compila sin tests y ejecuta la aplicación.
- `./run.sh -- run.sh --run-tests` — compila con tests y ejecuta la aplicación.
- `./run.sh -- <args>` — pasa argumentos a la aplicación.

El script compila con `mvn clean package` (con `-q` para output quiet) y ejecuta el JAR resultante con `java -jar`.

### Manual

```bash
mvn clean package
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
