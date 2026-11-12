package com.pedidos360.pedidos.producer.pedido;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.pedidos360.pedidos.dto.PedidoEvento;
import com.pedidos360.pedidos.producer.RabbitMQProducer;

/**
 * Productor del dominio de Pedidos.
 * Utiliza el RabbitMQProducer generico desacoplado para publicar eventos
 * con tipado fuerte del dominio Pedido.
 */
@Component
public class PedidoProducer {

    private static final Logger log = LoggerFactory.getLogger(PedidoProducer.class);

    private final RabbitMQProducer rabbitMQProducer;

    public PedidoProducer(RabbitMQProducer rabbitMQProducer) {
        this.rabbitMQProducer = rabbitMQProducer;
    }

    /**
     * Publica un evento de pedido hacia RabbitMQ.
     *
     * @param evento evento a publicar
     */
    public void publicarEvento(PedidoEvento evento) {
        log.info("Publicando evento de dominio para pedido ID: {}, tipo: {}",
                evento != null ? evento.idPedido() : null,
                evento != null ? evento.tipoEvento() : null);

        this.rabbitMQProducer.enviar(evento);
    }
}
