package com.pedidos360.pedidos.admin.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Exchange;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.HeadersExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.stereotype.Service;

import com.pedidos360.pedidos.admin.dto.BindingRequestDto;
import com.pedidos360.pedidos.admin.dto.ExchangeRequestDto;
import com.pedidos360.pedidos.admin.dto.QueueRequestDto;

/**
 * Servicio encargado de la administracion programatica de RabbitMQ.
 *
 * Encapsula la creacion y eliminacion de colas, exchanges y bindings
 * utilizando AmqpAdmin / RabbitAdmin.
 * Evita la mezcla de logica de infraestructura con los controladores.
 */
@Service
public class RabbitAdminService {

    private static final Logger log = LoggerFactory.getLogger(RabbitAdminService.class);

    private final AmqpAdmin amqpAdmin;

    public RabbitAdminService(AmqpAdmin amqpAdmin) {
        this.amqpAdmin = amqpAdmin;
    }

    /**
     * Declara una nueva cola en RabbitMQ con los parametros especificados.
     *
     * @param dto datos de la cola (nombre, durable, exclusive, autoDelete)
     * @return nombre de la cola creada
     */
    public String crearCola(QueueRequestDto dto) {
        Queue queue = new Queue(
                dto.name().trim(),
                dto.durable(),
                dto.exclusive(),
                dto.autoDelete()
        );

        log.info("Declarando cola en RabbitMQ: name={}, durable={}, exclusive={}, autoDelete={}",
                queue.getName(), queue.isDurable(), queue.isExclusive(), queue.isAutoDelete());

        return this.amqpAdmin.declareQueue(queue);
    }

    /**
     * Elimina una cola existente en RabbitMQ.
     *
     * @param name nombre de la cola a eliminar
     * @return true si la cola existia y fue eliminada, false de lo contrario
     */
    public boolean eliminarCola(String name) {
        log.info("Eliminando cola en RabbitMQ: {}", name);
        return this.amqpAdmin.deleteQueue(name.trim());
    }

    /**
     * Declara un nuevo exchange en RabbitMQ segun su tipo (direct, topic, fanout, headers).
     *
     * @param dto datos del exchange
     */
    public void crearExchange(ExchangeRequestDto dto) {
        String name = dto.name().trim();
        String type = dto.type().trim().toLowerCase();
        boolean durable = dto.durable();
        boolean autoDelete = dto.autoDelete();

        Exchange exchange = switch (type) {
            case "direct" -> new DirectExchange(name, durable, autoDelete);
            case "topic" -> new TopicExchange(name, durable, autoDelete);
            case "fanout" -> new FanoutExchange(name, durable, autoDelete);
            case "headers" -> new HeadersExchange(name, durable, autoDelete);
            default -> throw new IllegalArgumentException("Tipo de exchange no soportado: " + type);
        };

        log.info("Declarando exchange en RabbitMQ: name={}, type={}, durable={}, autoDelete={}",
                name, type, durable, autoDelete);

        this.amqpAdmin.declareExchange(exchange);
    }

    /**
     * Elimina un exchange existente en RabbitMQ.
     *
     * @param name nombre del exchange
     * @return true si el exchange existia y fue eliminado
     */
    public boolean eliminarExchange(String name) {
        log.info("Eliminando exchange en RabbitMQ: {}", name);
        return this.amqpAdmin.deleteExchange(name.trim());
    }

    /**
     * Declara una asociacion (Binding) entre una cola y un exchange con una routing key.
     *
     * @param dto datos del binding
     */
    public void crearBinding(BindingRequestDto dto) {
        Binding binding = new Binding(
                dto.queue().trim(),
                Binding.DestinationType.QUEUE,
                dto.exchange().trim(),
                dto.routingKey().trim(),
                null
        );

        log.info("Declarando binding en RabbitMQ: queue='{}' -> exchange='{}' con routingKey='{}'",
                dto.queue(), dto.exchange(), dto.routingKey());

        this.amqpAdmin.declareBinding(binding);
    }

    /**
     * Elimina una asociacion (Binding) en RabbitMQ.
     *
     * @param dto datos del binding a remover
     */
    public void eliminarBinding(BindingRequestDto dto) {
        Binding binding = new Binding(
                dto.queue().trim(),
                Binding.DestinationType.QUEUE,
                dto.exchange().trim(),
                dto.routingKey().trim(),
                null
        );

        log.info("Eliminando binding en RabbitMQ: queue='{}' -> exchange='{}' con routingKey='{}'",
                dto.queue(), dto.exchange(), dto.routingKey());

        this.amqpAdmin.removeBinding(binding);
    }
}
