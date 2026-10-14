package com.pedidos360.pedidos.producer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import com.pedidos360.pedidos.config.RabbitMQProperties;

/**
 * Productor generico desacoplado para RabbitMQ.
 *
 * Encapsula el uso de RabbitTemplate y centraliza la publicacion de mensajes
 * utilizando la topologia definida en RabbitMQProperties (cero hardcoding).
 */
@Component
public class RabbitMQProducer {

    private static final Logger log = LoggerFactory.getLogger(RabbitMQProducer.class);

    private final RabbitTemplate rabbitTemplate;
    private final RabbitMQProperties propiedades;

    public RabbitMQProducer(RabbitTemplate rabbitTemplate, RabbitMQProperties propiedades) {
        this.rabbitTemplate = rabbitTemplate;
        this.propiedades = propiedades;
    }

    /**
     * Envia un mensaje generico hacia el exchange y routing key principales configurados.
     *
     * @param <T> tipo generico del mensaje o evento
     * @param mensaje objeto que sera serializado automaticamente a JSON
     */
    public <T> void enviar(T mensaje) {
        this.enviar(this.propiedades.getExchange(), this.propiedades.getRoutingKey(), mensaje);
    }

    /**
     * Envia un mensaje generico hacia el exchange principal con una routing key personalizada.
     *
     * @param <T> tipo generico del mensaje
     * @param routingKey routing key de destino
     * @param mensaje objeto a serializar y enviar
     */
    public <T> void enviar(String routingKey, T mensaje) {
        this.enviar(this.propiedades.getExchange(), routingKey, mensaje);
    }

    /**
     * Envia un mensaje generico indicando exchange, routing key y carga util.
     *
     * @param <T> tipo generico del mensaje
     * @param exchange exchange de destino
     * @param routingKey routing key de destino
     * @param mensaje cuerpo o evento a enviar
     */
    public <T> void enviar(String exchange, String routingKey, T mensaje) {
        log.info("Publicando mensaje generico a RabbitMQ [exchange='{}', routingKey='{}']: {}",
                exchange, routingKey, mensaje);

        this.rabbitTemplate.convertAndSend(exchange, routingKey, mensaje);
    }
}
