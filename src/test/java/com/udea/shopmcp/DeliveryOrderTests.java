package com.udea.shopmcp;

import com.udea.shopmcp.models.DeliveryItem.Status;
import com.udea.shopmcp.repositories.DeliveryOrderRepository;
import com.udea.shopmcp.services.DeliveryToolsService;
import com.udea.shopmcp.services.DeliveryToolsService.RequestedItem;
import com.udea.shopmcp.services.FulfillmentToolsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.UUID;
import com.udea.shopmcp.models.Product;
import com.udea.shopmcp.repositories.ProductRepository;
import com.udea.shopmcp.services.InventoryToolsService;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = "SHOPMCP_GEMINI_API_KEY=test-gemini-key")
class DeliveryOrderTests {
    @Autowired DeliveryToolsService deliveries;
    @Autowired FulfillmentToolsService fulfillment;
    @Autowired DeliveryOrderRepository orders;
    @Autowired ProductRepository products;
    @Autowired InventoryToolsService inventory;

    @Test
    void includesTheSampleCatalog() {
        assertTrue(inventory.searchInventory(null).size() >= 50);
        assertTrue(inventory.searchInventory("cargamanto").stream()
                .anyMatch(product -> product.getName().equals("Fríjol cargamanto")));
    }

    @Test
    void tracksEachLineAcrossTransactions() {
        String suffix = UUID.randomUUID().toString();
        Product milk = products.save(new Product("Leche " + suffix, "Producto de prueba", 2));
        Product rice = products.save(new Product("Arroz " + suffix, "Producto de prueba", 1));
        UUID id = null;
        try {
            var receipt = deliveries.placeOrder("Calle 10 # 20-30", List.of(
                    new RequestedItem(milk.getName(), 2), new RequestedItem(rice.getName(), 1)));
            id = receipt.trackingId();
            assertEquals(0, products.findById(milk.getId()).orElseThrow().getQuantity());
            fulfillment.restockProduct(milk.getId(), 3);
            var available = inventory.listAvailableProducts();
            assertTrue(available.stream().anyMatch(p -> p.getId().equals(milk.getId()) && p.getQuantity() == 3));
            assertFalse(available.stream().anyMatch(p -> p.getId().equals(rice.getId())));
            assertThrows(IllegalArgumentException.class, () -> deliveries.placeOrder("Calle 10", List.of(
                    new RequestedItem(milk.getName(), 1), new RequestedItem(rice.getName(), 1))));
            assertEquals(3, products.findById(milk.getId()).orElseThrow().getQuantity());
            assertEquals(receipt, deliveries.trackOrder(id));
            assertThrows(IllegalArgumentException.class,
                    () -> fulfillment.updateItemStatus(receipt.trackingId(), 1, Status.DELIVERED));
            for (Status status : List.of(Status.PREPARING, Status.OUT_FOR_DELIVERY, Status.DELIVERED)) {
                fulfillment.updateItemStatus(id, 1, status);
            }
            var items = deliveries.trackOrder(id).items();
            assertEquals(Status.DELIVERED, items.getFirst().status());
            assertEquals("Entregado", items.getFirst().statusLabel());
            assertEquals(Status.ORDERED, items.getLast().status());
        } finally {
            if (id != null) orders.deleteById(id);
            products.deleteAllById(List.of(milk.getId(), rice.getId()));
        }
    }
}
