package com.udea.shopmcp.models;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "delivery_order")
@Getter
@NoArgsConstructor
public class DeliveryOrder {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    private String address;

    @ElementCollection
    @OrderColumn(name = "line_index")
    private List<DeliveryItem> items = new ArrayList<>();

    @Version
    private Long version;

    public DeliveryOrder(String address, List<DeliveryItem> items) {
        this.address = address;
        this.items.addAll(items);
    }
}
