package com.pedidos360.pedidos.kafka.admin.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/**
 * Payload de entrada para la creación dinámica de un tópico Kafka.
 * Validado con Bean Validation antes de llegar al servicio.
 */
public record CrearTopicoRequest(

        @NotBlank(message = "El nombre del tópico es obligatorio")
        String nombre,

        @Min(value = 1, message = "Debe tener al menos 1 partición")
        int particiones,

        @Min(value = 1, message = "El factor de replicación debe ser al menos 1")
        short replicacion
) {}

