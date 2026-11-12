package com.pedidos360.pedidos.admin.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * DTO para la creacion de exchanges en RabbitMQ.
 * Valida nombre, durabilidad y que el tipo de exchange sea direct, topic, fanout o headers.
 */
public record ExchangeRequestDto(
        @NotBlank(message = "El nombre del exchange es obligatorio")
        @Size(min = 3, max = 120, message = "El nombre del exchange debe tener entre 3 y 120 caracteres")
        @JsonProperty("name")
        String name,

        @NotBlank(message = "El tipo de exchange es obligatorio")
        @Pattern(
                regexp = "^(?i)(direct|topic|fanout|headers)$",
                message = "El tipo de exchange debe ser direct, topic, fanout o headers"
        )
        @JsonProperty("type")
        String type,

        @JsonProperty("durable")
        Boolean durable,

        @JsonProperty("autoDelete")
        Boolean autoDelete
) {

    public ExchangeRequestDto {
        if (durable == null) {
            durable = true;
        }
        if (autoDelete == null) {
            autoDelete = false;
        }
    }
}
