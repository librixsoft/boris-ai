# Boris CLI

Asistente de linea de comandos en Java con colores y ASCII art.

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

## Opciones

| Flag | Description |
|------|-------------|
| `-n, --name <nombre>` | Tu nombre (default: mundo) |
| `-v, --verbose` | Modo detallado |
| `--help` | Ayuda |
| `--version` | Version |

## Ejemplos

```bash
java -jar target/boris-cli-1.0.0.jar -n Boris
java -jar target/boris-cli-1.0.0.jar --name Mundo --verbose
java -jar target/boris-cli-1.0.0.jar --help
```

## Stack

- **Picocli** 4.7.6 — parsing de argumentos CLI
- **Jansi** 2.4.1 — soporte de colores en terminal
