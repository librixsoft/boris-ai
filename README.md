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

## Stack

- **Picocli** 4.7.6 — parsing de argumentos CLI
- ANSI sequences — soporte de colores en terminal

## Autor

Creado por **Anibal Gomez** — anibal@librixsoft.com
