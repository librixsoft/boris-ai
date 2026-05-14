Quiero ejecutar una tarea con 3 roles:

- planner: solo define pasos (sin código, sin JSON).
- coder: convierte pasos a JSON válido con este formato:
  {"tasks":[{"id":1,"description":"...","file":"...","code":"..."}]}
- writer: crea/edita archivos usando ese JSON.

RUTA DE SALIDA OBLIGATORIA:
C:\Users\lastprophet\Downloads\hello-multimodel
No escribas fuera de esa ruta.

Tarea:
Crear un Hola Mundo en Java 21.

Archivo esperado:
src/main/java/com/example/hello/Main.java

Contenido esperado:
- Clase Main
- Método main
- Imprimir exactamente: Hola mundo desde multi-model pipeline

Reglas:
- planner responde solo lista numerada corta (3-5 pasos)
- coder responde solo JSON (sin markdown)
- writer escribe archivo y al final da resumen con ruta absoluta creada