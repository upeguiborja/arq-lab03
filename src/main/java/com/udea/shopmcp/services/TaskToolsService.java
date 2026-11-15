package com.udea.shopmcp.services;

import com.udea.shopmcp.models.Task;
import com.udea.shopmcp.repositories.TaskRepository;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;

@Component
public class TaskToolsService {
    private final TaskRepository taskRepository;

    public TaskToolsService(TaskRepository taskRepository) {
        this.taskRepository = taskRepository;
    }

    @Tool(description = "Lista todas las tareas guardadas.")
    public List<Task> listTasks() {
        return taskRepository.findAll();
    }

    @Tool(description = "Cuenta las tareas guardadas.")
    public long countTasks() {
        return taskRepository.count();
    }

    @Tool(description =  "Lista las tareas pendientes.")
    public List<Task> listPending() {
        return taskRepository.findByCompleted(false);
    }

    @Tool(description = "Lista las tareas completadas.")
    public List<Task> listCompletedTasks() {
        return taskRepository.findByCompleted(true);
    }

    @Tool(description = "Busca tareas que contengan el texto indicado en el título.")
    public List<Task> searchTasks(
            @ToolParam(description = "Texto que se busca en el título de la tarea.")
            String fragment
    ) {
        return taskRepository.findByTitleContainingIgnoreCase(fragment);
    }

    @Tool(description = "Crea una tarea pendiente con un título y una fecha.")
    public Task createTask(
            @ToolParam(description = "Título de la tarea.") String title,
            @ToolParam(description = "Fecha en formato aaaa-mm-dd, por ejemplo, 2026-11-20.") String date
    ) {
        if (title == null || title.isBlank()) {
            throw  new IllegalArgumentException("El título es obligatorio.");
        }

        return taskRepository.save(new Task(title.trim(), validateDate(date), false));
    }

    @Tool(description = "Marca la tarea indicada como completada.")
    public Task markCompleted(
            @ToolParam(description = "Identificador de la tarea que se va a completar.") Long id
    ) {
        Task t = taskRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("No existe la tarea con el identificador " + id));
        t.setCompleted(true);
        return taskRepository.save(t);
    }

    @Tool(description = "Actualiza el título o la fecha de una tarea.")
    public Task updateTask(
            @ToolParam(description = "Identificador de la tarea que se va a actualizar.") Long id,
            @ToolParam(description = "Nuevo título. Usa null para conservar el título actual.") String title,
            @ToolParam(description = "Nueva fecha en formato aaaa-mm-dd. Usa null para conservar la fecha actual.") String date
    ) {
        Task t = taskRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("No existe la tarea con el identificador " + id));
        String validatedDate = date == null ? null : validateDate(date);
        if (title != null && !title.isBlank()) t.setTitle(title.trim());
        if (validatedDate != null) t.setDate(validatedDate);
        return taskRepository.save(t);
    }

    private String validateDate(String date) {
        if (date == null || !date.matches("\\d{4}-\\d{2}-\\d{2}")) {
            throw new IllegalArgumentException("Escribe la fecha en formato aaaa-mm-dd.");
        }
        try {
            LocalDate.parse(date);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Escribe una fecha válida en formato aaaa-mm-dd.", e);
        }
        return date;
    }

    @Tool(description = "Elimina la tarea indicada.")
    public String deleteTask(
            @ToolParam(description = "Identificador de la tarea que se va a eliminar.") Long id
    ) {
        if (!taskRepository.existsById(id)) return "La tarea " + id + " no existe.";
        taskRepository.deleteById(id);
        return "Se eliminó la tarea " + id + ".";
    }
}
