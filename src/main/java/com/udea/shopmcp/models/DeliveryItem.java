package com.udea.shopmcp.models;

import jakarta.persistence.Embeddable;
import jakarta.persistence.FetchType;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Embeddable
@Getter
@NoArgsConstructor
public class DeliveryItem {
    public enum Status {
        ORDERED("Recibido"),
        PREPARING("En preparación"),
        OUT_FOR_DELIVERY("En camino"),
        DELIVERED("Entregado");

        @Getter
        private final String label;

        Status(String label) {
            this.label = label;
        }
    }

    @ManyToOne(fetch = FetchType.LAZY)
    private Product catalogProduct;

    private String product;
    private int quantity;

    @Enumerated(EnumType.STRING)
    private Status status;

    public DeliveryItem(Product product, int quantity) {
        this.catalogProduct = product;
        this.product = product.getName();
        this.quantity = quantity;
        this.status = Status.ORDERED;
    }

    public void updateStatus(Status requested) {
        if (requested == status) return;
        if (requested == null || requested.ordinal() != status.ordinal() + 1) {
            throw new IllegalArgumentException("El estado de entrega solo puede avanzar a la etapa siguiente.");
        }
        status = requested;
    }
}
