package com.udea.shopmcp.services;

import com.udea.shopmcp.models.DeliveryItem.Status;
import com.udea.shopmcp.models.DeliveryItem;
import com.udea.shopmcp.models.DeliveryOrder;
import com.udea.shopmcp.models.Product;
import com.udea.shopmcp.repositories.DeliveryOrderRepository;
import com.udea.shopmcp.repositories.ProductRepository;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Component
@Transactional
public class DeliveryToolsService {
    public record RequestedItem(String product, int quantity) {}
    public record ItemStatus(int lineNumber, String product, int quantity, Status status, String statusLabel) {}
    public record Receipt(UUID trackingId, List<ItemStatus> items) {}

    private final DeliveryOrderRepository orders;
    private final ProductRepository products;

    public DeliveryToolsService(DeliveryOrderRepository orders, ProductRepository products) {
        this.orders = orders;
        this.products = products;
    }

    @Tool(description = "Registra un pedido de supermercado a domicilio y devuelve el código de seguimiento y los productos.")
    public Receipt placeOrder(@ToolParam(description = "Dirección completa de entrega.") String address,
                              @ToolParam(description = "Productos y cantidad de unidades de cada uno. Usa números enteros positivos.") List<RequestedItem> items) {
        if (address == null || address.isBlank() || address.strip().length() > 255) {
            throw new IllegalArgumentException("Escribe una dirección de entrega de máximo 255 caracteres.");
        }
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("El pedido debe incluir al menos un producto.");
        }
        var productIds = items.stream().map(item -> {
            if (item == null || item.product() == null || item.product().isBlank()
                    || item.product().strip().length() > 255 || item.quantity() < 1) {
                throw new IllegalArgumentException("Cada producto debe tener un nombre de máximo 255 caracteres y una cantidad entera mayor que cero.");
            }
            return products.findIdByName(item.product().strip())
                    .orElseThrow(() -> new IllegalArgumentException("El producto no está en el catálogo: " + item.product()));
        }).toList();
        Map<Long, Product> stock = products.lockForUpdate(productIds).stream()
                .collect(Collectors.toMap(Product::getId, Function.identity()));
        var lines = new ArrayList<DeliveryItem>();
        for (int index = 0; index < items.size(); index++) {
            Product product = stock.get(productIds.get(index));
            int units = items.get(index).quantity();
            product.reserve(units);
            lines.add(new DeliveryItem(product, units));
        }
        return receipt(orders.save(new DeliveryOrder(address.strip(), lines)));
    }

    @Tool(description = "Consulta el estado de entrega de cada producto con el código de seguimiento del pedido.")
    @Transactional(readOnly = true)
    public Receipt trackOrder(@ToolParam(description = "Código de seguimiento del pedido.") UUID trackingId) {
        return receipt(findOrder(trackingId));
    }

    private DeliveryOrder findOrder(UUID trackingId) {
        if (trackingId == null) throw new IllegalArgumentException("Ingresa el código de seguimiento del pedido.");
        return orders.findById(trackingId)
                .orElseThrow(() -> new IllegalArgumentException("No se encontró ningún pedido con el código " + trackingId));
    }

    public Receipt updateItemStatus(UUID trackingId, int lineNumber, Status status) {
        DeliveryOrder order = findOrder(trackingId);
        if (lineNumber < 1 || lineNumber > order.getItems().size()) {
            throw new IllegalArgumentException("El pedido no contiene un producto con el número de línea " + lineNumber);
        }
        order.getItems().get(lineNumber - 1).updateStatus(status);
        return receipt(order);
    }

    private Receipt receipt(DeliveryOrder order) {
        var items = order.getItems();
        return new Receipt(order.getId(), IntStream.range(0, items.size()).mapToObj(index -> {
            DeliveryItem item = items.get(index);
            return new ItemStatus(index + 1, item.getProduct(), item.getQuantity(), item.getStatus(), item.getStatus().getLabel());
        }).toList());
    }
}
