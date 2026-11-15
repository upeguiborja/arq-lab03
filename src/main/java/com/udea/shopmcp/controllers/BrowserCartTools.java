package com.udea.shopmcp.controllers;

import org.springframework.ai.tool.annotation.Tool;
import java.util.function.Function;

public class BrowserCartTools {
    private final Function<String, Object> invoke;

    BrowserCartTools(Function<String, Object> invoke) {
        this.invoke = invoke;
    }

    @Tool(description = "Consulta en el navegador los productos y cantidades que el usuario tiene ahora en su carrito.")
    public Object getCart() {
        return invoke.apply("getCart");
    }

    @Tool(description = "Lista los códigos de los pedidos realizados desde este navegador, del más reciente al más antiguo. Para consultar su estado usa trackOrder con el código.")
    public Object listOrderIds() {
        return invoke.apply("listOrderIds");
    }

    @Tool(description = "Abre el carrito en el navegador cuando el usuario pida verlo. Devuelve su contenido.")
    public Object openCart() {
        return invoke.apply("openCart");
    }
}
