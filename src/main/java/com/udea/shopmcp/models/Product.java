package com.udea.shopmcp.models;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "product")
@Getter
@NoArgsConstructor
public class Product {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    @Column(nullable = false)
    private String description;

    @Column(nullable = false)
    private int quantity;

    public Product(String name, String description, int quantity) {
        this.name = name;
        this.description = description;
        this.quantity = quantity;
    }

    public void reserve(int units) {
        if (units < 1 || units > quantity) {
            throw new IllegalArgumentException("Cantidad no disponible para " + name + ". Unidades disponibles: " + quantity);
        }
        quantity -= units;
    }

    public void restock(int units) {
        if (units < 1 || units > Integer.MAX_VALUE - quantity) {
            throw new IllegalArgumentException("Ingresa una cantidad positiva que no supere la capacidad del inventario.");
        }
        quantity += units;
    }
}
