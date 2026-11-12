package com.pedidos360.pedidos.kafka.dto;

import java.time.Instant;

/**
 * DTO inmutable que viaja en el tópico {@code pedidos.auditoria}.
 * Se serializa/deserializa como JSON por el JsonSerializer de Spring Kafka.
 */
public record PedidoAuditoriaDto(
        Long pedidoId,
        String accion,
        String realizadoPor,
        Instant timestamp,
        String detalle
) {}

