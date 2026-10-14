package com.pedidos360.pedidos.config;

import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Beans de topologia RabbitMQ (colas, exchanges, bindings) y conversor JSON.
 *
 * Separado de la logica de negocio: PedidoService y los controladores
 * no declaran ni conocen esta infraestructura.
 */
@Configuration
@EnableConfigurationProperties(RabbitMQProperties.class)
public class RabbitMQConfig {

    /** Claves AMQP del protocolo; no son nombres de topologia. */
    private static final String ARG_DEAD_LETTER_EXCHANGE = "x-dead-letter-exchange";
    private static final String ARG_DEAD_LETTER_ROUTING_KEY = "x-dead-letter-routing-key";

    private final RabbitMQProperties propiedades;

    public RabbitMQConfig(RabbitMQProperties propiedades) {
        this.propiedades = propiedades;
    }

    @Bean
    public Queue deadLetterQueue() {
        return QueueBuilder.durable(this.propiedades.getDlq().getQueue()).build();
    }

    @Bean
    public DirectExchange deadLetterExchange() {
        return new DirectExchange(this.propiedades.getDlq().getExchange(), true, false);
    }

    @Bean
    public Binding deadLetterBinding() {
        return BindingBuilder
                .bind(this.deadLetterQueue())
                .to(this.deadLetterExchange())
                .with(this.propiedades.getDlq().getRoutingKey());
    }

    @Bean
    public Queue pedidosQueue() {
        return QueueBuilder.durable(this.propiedades.getQueue())
                .withArgument(ARG_DEAD_LETTER_EXCHANGE, this.propiedades.getDlq().getExchange())
                .withArgument(ARG_DEAD_LETTER_ROUTING_KEY, this.propiedades.getDlq().getRoutingKey())
                .build();
    }

    @Bean
    public DirectExchange pedidosExchange() {
        return new DirectExchange(this.propiedades.getExchange(), true, false);
    }

    @Bean
    public Binding pedidosBinding() {
        return BindingBuilder
                .bind(this.pedidosQueue())
                .to(this.pedidosExchange())
                .with(this.propiedades.getRoutingKey());
    }

    @Bean
    public Jackson2JsonMessageConverter jackson2JsonMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(
            ConnectionFactory connectionFactory,
            Jackson2JsonMessageConverter converter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(converter);
        return template;
    }

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory,
            Jackson2JsonMessageConverter converter) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(converter);
        factory.setAcknowledgeMode(AcknowledgeMode.MANUAL);
        return factory;
    }

    @Bean
    public AmqpAdmin amqpAdmin(ConnectionFactory connectionFactory) {
        return new RabbitAdmin(connectionFactory);
    }
}
