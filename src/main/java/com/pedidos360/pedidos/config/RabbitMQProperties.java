package com.pedidos360.pedidos.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Nombres de colas, exchanges y routing keys de RabbitMQ.
 *
 * Fuente unica: app.rabbitmq.* en application.yml.
 * El resto del codigo inyecta esta clase; no debe haber strings de
 * topologia sueltos.
 */
@ConfigurationProperties(prefix = "app.rabbitmq")
public class RabbitMQProperties {

    private String queue;
    private String exchange;
    private String routingKey;
    private final Dlq dlq = new Dlq();

    public String getQueue() {
        return this.queue;
    }

    public void setQueue(String queue) {
        this.queue = queue;
    }

    public String getExchange() {
        return this.exchange;
    }

    public void setExchange(String exchange) {
        this.exchange = exchange;
    }

    public String getRoutingKey() {
        return this.routingKey;
    }

    public void setRoutingKey(String routingKey) {
        this.routingKey = routingKey;
    }

    public Dlq getDlq() {
        return this.dlq;
    }

    public static class Dlq {

        private String queue;
        private String exchange;
        private String routingKey;

        public String getQueue() {
            return this.queue;
        }

        public void setQueue(String queue) {
            this.queue = queue;
        }

        public String getExchange() {
            return this.exchange;
        }

        public void setExchange(String exchange) {
            this.exchange = exchange;
        }

        public String getRoutingKey() {
            return this.routingKey;
        }

        public void setRoutingKey(String routingKey) {
            this.routingKey = routingKey;
        }
    }
}
