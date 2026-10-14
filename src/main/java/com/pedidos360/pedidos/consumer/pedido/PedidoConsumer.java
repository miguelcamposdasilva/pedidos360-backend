package com.pedidos360.pedidos.consumer.pedido;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import com.pedidos360.pedidos.dto.PedidoEvento;
import com.pedidos360.pedidos.service.PedidoService;
import com.rabbitmq.client.Channel;

/**
 * Consumidor de RabbitMQ para el dominio de Pedidos.
 *
 * Cumple con los requisitos de la arquitectura:
 * - Agrupado en paquete por dominio funcional (consumer.pedido).
 * - Configuracion sin hardcoding mediante SpEL/property placeholder (${app.rabbitmq.queue}).
 * - Modo de confirmacion manual (AcknowledgeMode.MANUAL) con inyeccion de Channel y DeliveryTag.
 * - Gestion explicita de ACK ante exito y NACK (requeue = false) ante error para desvio a la DLQ.
 * - Desacoplado: delega toda la logica de negocio en PedidoService.
 */
@Component
public class PedidoConsumer {

    private static final Logger log = LoggerFactory.getLogger(PedidoConsumer.class);

    private final PedidoService pedidoService;

    public PedidoConsumer(PedidoService pedidoService) {
        this.pedidoService = pedidoService;
    }

    /**
     * Procesa eventos de notificacion de pedidos desde la cola principal de RabbitMQ.
     *
     * @param evento datos del evento serializado en JSON
     * @param channel canal AMQP para confirmaciones manuales
     * @param tag etiqueta de entrega del mensaje (delivery tag)
     * @throws IOException ante fallos de comunicacion en el canal AMQP
     */
    @RabbitListener(
            queues = "${app.rabbitmq.queue}",
            ackMode = "MANUAL"
    )
    public void procesarPedido(
            @Payload PedidoEvento evento,
            Channel channel,
            @Header(AmqpHeaders.DELIVERY_TAG) long tag) throws IOException {

        log.info("Mensaje recibido de RabbitMQ [DeliveryTag={}]: {}", tag, evento);

        try {
            // Invocar el servicio de negocio de forma limpia sin mezclar logica AMQP
            this.pedidoService.procesarEvento(evento);

            // Confirmacion positiva manual (ACK)
            channel.basicAck(tag, false);
            log.info("Mensaje procesado y confirmado con éxito (ACK) [DeliveryTag={}]. Pedido ID: {}",
                    tag, evento != null ? evento.idPedido() : null);

        } catch (Exception e) {
            // Registro detallado del error en logs mediante SLF4J
            log.error("Error al procesar el mensaje de RabbitMQ [DeliveryTag={}]. Ejecutando basicNack (requeue=false) para desviar a DLQ. Causa: {}",
                    tag, e.getMessage(), e);

            // Confirmacion negativa manual (NACK) con requeue = false.
            // Gracias a los argumentos x-dead-letter-exchange y x-dead-letter-routing-key
            // configurados en la cola, RabbitMQ redirige el mensaje automaticamente a la DLQ.
            channel.basicNack(tag, false, false);
        }
    }
}
