package com.boris.task;

/**
 * Estados de ejecucion del agente para garantizar que las tareas no se detengan
 * prematuramente con modelos pequenos.
 */
public enum TaskExecutionState {
    /**
     * Estado inicial o conversacion directa (chat sin herramientas).
     */
    IDLE,

    /**
     * Se ha invocado al menos una herramienta en la interaccion actual; hay una tarea en curso.
     */
    TASK_IN_PROGRESS,

    /**
     * La tarea fue completada satisfactoriamente (se aplicaron modificaciones reales en el codigo).
     */
    COMPLETED,

    /**
     * El agente se detuvo tras agotar los 3 intentos sin haber aplicado cambios en el proyecto.
     */
    INCOMPLETE
}
