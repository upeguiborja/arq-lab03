package com.udea.shopmcp.repositories;

import com.udea.shopmcp.models.Task;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TaskRepository extends JpaRepository<Task, Long> {
    List<Task> findByCompleted(Boolean completed);
    List<Task> findByTitleContainingIgnoreCase(String fragment);
}
