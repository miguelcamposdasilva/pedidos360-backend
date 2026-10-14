package com.pedidos360.pedidos.admin.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * DTO para la creacion y asociacion de bindings en RabbitMQ.
 * Valida que la cola, exchange y routing key sean validos.
 */
public record BindingRequestDto(
        @NotBlank(message = "El nombre de la cola es obligatorio")
        @Size(min = 3, max = 120, message = "El nombre de la cola debe tener entre 3 y 120 caracteres")
        @JsonProperty("queue")
        String queue,

        @NotBlank(message = "El nombre del exchange es obligatorio")
        @Size(min = 3, max = 120, message = "El nombre del exchange debe tener entre 3 y 120 caracteres")
        @JsonProperty("exchange")
        String exchange,

        @NotNull(message = "La routing key es obligatoria")
        @JsonProperty("routingKey")
        String routingKey
) {
}
