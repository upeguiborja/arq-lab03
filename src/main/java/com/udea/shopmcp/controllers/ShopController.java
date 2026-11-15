package com.udea.shopmcp.controllers;

import com.udea.shopmcp.services.DeliveryToolsService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/shop")
public class ShopController {
    private final DeliveryToolsService deliveries;

    public ShopController(DeliveryToolsService deliveries) {
        this.deliveries = deliveries;
    }

    public record OrderRequest(String address, List<DeliveryToolsService.RequestedItem> items) {}

    @PostMapping("/orders")
    @ResponseStatus(HttpStatus.CREATED)
    public DeliveryToolsService.Receipt order(@RequestBody OrderRequest request) {
        return deliveries.placeOrder(request.address(), request.items());
    }

    @GetMapping("/orders/{id}")
    public ResponseEntity<?> track(@PathVariable UUID id) {
        try {
            return ResponseEntity.ok(deliveries.trackOrder(id));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("message", e.getMessage()));
        }
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> invalidOrder(IllegalArgumentException e) {
        return Map.of("message", e.getMessage());
    }
}
