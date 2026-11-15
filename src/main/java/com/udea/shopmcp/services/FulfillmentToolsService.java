package com.udea.shopmcp.services;

import com.udea.shopmcp.models.DeliveryItem.Status;
import com.udea.shopmcp.models.Product;
import com.udea.shopmcp.repositories.ProductRepository;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Component
public class FulfillmentToolsService {
    private final DeliveryToolsService deliveries;
    private final ProductRepository products;

    public FulfillmentToolsService(DeliveryToolsService deliveries, ProductRepository products) {
        this.deliveries = deliveries;
        this.products = products;
    }

    @Tool(description = "Registra la llegada de nuevas unidades de un producto al supermercado.")
    @Transactional
    public Product restockProduct(
            @ToolParam(description = "Identificador del producto en el catálogo.") Long productId,
            @ToolParam(description = "Número de unidades recibidas.") int quantity) {
        if (productId == null) throw new IllegalArgumentException("Ingresa el identificador del producto.");
        Product product = products.lockForUpdate(List.of(productId)).stream().findFirst()
                .orElseThrow(() -> new IllegalArgumentException("El producto no está en el catálogo."));
        product.restock(quantity);
        return product;
    }

    @Tool(description = "Actualiza el estado de un producto según su número de línea en el pedido. Los estados, en orden, son recibido (ORDERED), en preparación (PREPARING), en camino (OUT_FOR_DELIVERY) y entregado (DELIVERED).")
    public DeliveryToolsService.Receipt updateItemStatus(
            @ToolParam(description = "Código de seguimiento del pedido.") UUID trackingId,
            @ToolParam(description = "Número de línea del producto, a partir de 1.") int lineNumber,
            @ToolParam(description = "Nuevo estado de entrega del producto.") Status status) {
        return deliveries.updateItemStatus(trackingId, lineNumber, status);
    }
}
