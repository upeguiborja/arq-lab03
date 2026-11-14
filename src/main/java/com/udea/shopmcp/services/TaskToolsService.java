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

    @Tool(description = "Returns the complete list of stored tasks.")
    public List<Task> listTasks() {
        return taskRepository.findAll();
    }

    @Tool(description = "Returns the total number of stored tasks.")
    public long countTasks() {
        return taskRepository.count();
    }

    @Tool(description =  "Returns only the pending tasks.")
    public List<Task> listPending() {
        return taskRepository.findByCompleted(false);
    }

    @Tool(description = "Returns only the completed tasks.")
    public List<Task> listCompletedTasks() {
        return taskRepository.findByCompleted(true);
    }

    @Tool(description = "Search for tasks whose title contains the given fragment.")
    public List<Task> searchTasks(
            @ToolParam(description = "Text fragment to be searched in the task's title.")
            String fragment
    ) {
        return taskRepository.findByTitleContainingIgnoreCase(fragment);
    }

    @Tool(description = "Creates a new pending task with title and date")
    public Task createTask(
            @ToolParam(description = "Descriptive title for the task") String title,
            @ToolParam(description = "Date in yyyy-MM-dd format, for example 2026-11-20") String date
    ) {
        if (title == null || title.isBlank()) {
            throw  new IllegalArgumentException("Title is mandatory.");
        }

        return taskRepository.save(new Task(title.trim(), validateDate(date), false));
    }

    @Tool(description = "Marks a task with the given id as completed.")
    public Task markCompleted(
            @ToolParam(description = "Unique identifier for the task to be marked complete.") Long id
    ) {
        Task t = taskRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("The task does not exist: " + id));
        t.setCompleted(true);
        return taskRepository.save(t);
    }

    @Tool(description = "Updates the title or date of an existing task.")
    public Task updateTask(
            @ToolParam(description = "Unique identifier of the task to be updated.") Long id,
            @ToolParam(description = "New title, use `null` to keep the original.") String title,
            @ToolParam(description = "New date in yyyy-MM-dd format, use `null` to keep the original.") String date
    ) {
        Task t = taskRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("The task does not exist: " + id));
        String validatedDate = date == null ? null : validateDate(date);
        if (title != null && !title.isBlank()) t.setTitle(title.trim());
        if (validatedDate != null) t.setDate(validatedDate);
        return taskRepository.save(t);
    }

    private String validateDate(String date) {
        if (date == null || !date.matches("\\d{4}-\\d{2}-\\d{2}")) {
            throw new IllegalArgumentException("Date must be in yyyy-MM-dd format.");
        }
        try {
            LocalDate.parse(date);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("Date must be a valid calendar date in yyyy-MM-dd format.", e);
        }
        return date;
    }

    @Tool(description = "Deletes the task with the given id.")
    public String deleteTask(
            @ToolParam(description = "Unique identifier of the task to be deleted.") Long id
    ) {
        if (!taskRepository.existsById(id)) return "Task " + id + "does not exist.";
        taskRepository.deleteById(id);
        return "Task " + id + " deleted successfully.";
    }
}
