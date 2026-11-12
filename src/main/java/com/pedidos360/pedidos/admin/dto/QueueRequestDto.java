package com.pedidos360.pedidos.admin.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * DTO para la creacion de colas en RabbitMQ.
 * Valida los datos de entrada para evitar nombres vacios o configuraciones invalidas.
 */
public record QueueRequestDto(
        @NotBlank(message = "El nombre de la cola es obligatorio")
        @Size(min = 3, max = 120, message = "El nombre de la cola debe tener entre 3 y 120 caracteres")
        @JsonProperty("name")
        String name,

        @JsonProperty("durable")
        Boolean durable,

        @JsonProperty("exclusive")
        Boolean exclusive,

        @JsonProperty("autoDelete")
        Boolean autoDelete
) {

    public QueueRequestDto {
        if (durable == null) {
            durable = true;
        }
        if (exclusive == null) {
            exclusive = false;
        }
        if (autoDelete == null) {
            autoDelete = false;
        }
    }
}
