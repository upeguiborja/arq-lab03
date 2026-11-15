package com.udea.shopmcp.models;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "task")
@Getter
@Setter
@NoArgsConstructor
public class Task {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "El título es obligatorio.")
    private String title;
    private String date;
    private Boolean completed;

    @Version
    private Long version;

    public Task(String title, String date, Boolean completed) {
        this.title = title;
        this.date = date;
        this.completed = completed;
    }
}
