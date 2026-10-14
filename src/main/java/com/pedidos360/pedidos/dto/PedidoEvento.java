package com.pedidos360.pedidos.dto;

import java.math.BigDecimal;
import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.pedidos360.pedidos.model.EstadoPedido;
import com.pedidos360.pedidos.model.Pedido;

/**
 * Evento de dominio para la integracion asincrona con RabbitMQ.
 * Transporta los datos del pedido entre productores y consumidores
 * con soporte para serializacion/deserializacion JSON limpia.
 */
public record PedidoEvento(
        @JsonProperty("idPedido") Long idPedido,
        @JsonProperty("cliente") String cliente,
        @JsonProperty("descripcion") String descripcion,
        @JsonProperty("monto") BigDecimal monto,
        @JsonProperty("estado") EstadoPedido estado,
        @JsonProperty("creadoPor") String creadoPor,
        @JsonProperty("fechaEvento") Instant fechaEvento,
        @JsonProperty("tipoEvento") String tipoEvento
) {

    public PedidoEvento {
        if (fechaEvento == null) {
            fechaEvento = Instant.now();
        }
    }

    /**
     * Construye un evento a partir de una entidad Pedido existente.
     */
    public static PedidoEvento de(Pedido pedido, String tipoEvento) {
        return new PedidoEvento(
                pedido.getId(),
                pedido.getCliente(),
                pedido.getDescripcion(),
                pedido.getMonto(),
                pedido.getEstado(),
                pedido.getCreadoPor(),
                Instant.now(),
                tipoEvento
        );
    }
}
