package com.pedidos360.pedidos.kafka.dto;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * DTO inmutable que viaja en el tópico {@code pedidos.eventos}.
 * Se serializa/deserializa como JSON por el JsonSerializer de Spring Kafka.
 */
public record PedidoEventoDto(
        Long pedidoId,
        String cliente,
        String descripcion,
        BigDecimal monto,
        String estado,
        String creadoPor,
        Instant timestamp,
        String tipoEvento
) {}

