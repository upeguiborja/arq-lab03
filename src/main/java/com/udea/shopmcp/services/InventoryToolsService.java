package com.udea.shopmcp.services;

import com.udea.shopmcp.models.Product;
import com.udea.shopmcp.repositories.ProductRepository;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class InventoryToolsService {
    private final ProductRepository products;

    public InventoryToolsService(ProductRepository products) {
        this.products = products;
    }

    @Tool(description = "Lista por nombre los productos que se pueden pedir ahora, con su descripción y la cantidad disponible.")
    public List<Product> listAvailableProducts() {
        return products.findByQuantityGreaterThanOrderByName(0);
    }

    @Tool(description = "Consulta el catálogo del supermercado con la descripción y las unidades disponibles de cada producto.")
    public List<Product> searchInventory(
            @ToolParam(required = false, description = "Parte del nombre del producto. Omite el texto para ver todo el catálogo.") String fragment) {
        return products.findByNameContainingIgnoreCaseOrderByName(fragment == null ? "" : fragment.strip());
    }
}
