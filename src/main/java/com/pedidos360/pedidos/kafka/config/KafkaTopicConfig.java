package com.pedidos360.pedidos.kafka.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Declara los tópicos de Kafka al arrancar la aplicación.
 * Los nombres se leen desde application.yml (app.kafka.*) para evitar
 * cualquier valor hardcodeado en código.
 *
 * <p>Spring Kafka crea los tópicos automáticamente si no existen,
 * respetando las configuraciones de retención aquí definidas.</p>
 */
@Configuration
public class KafkaTopicConfig {

    /** Nombre del tópico de eventos de pedidos (ej. {@code pedidos.eventos}). */
    @Value("${app.kafka.topico-pedidos}")
    private String topicoPedidos;

    /** Nombre del tópico de auditoría (ej. {@code pedidos.auditoria}). */
    @Value("${app.kafka.topico-auditoria}")
    private String topicoAuditoria;

    /**
     * Tópico principal de eventos de pedidos.
     * <ul>
     *   <li>3 particiones → mayor paralelismo de consumo</li>
     *   <li>1 réplica     → adecuado para entorno de desarrollo/single-broker</li>
     *   <li>retention.ms  → 7 días (604 800 000 ms)</li>
     *   <li>cleanup.policy → delete (mensajes se eliminan al expirar)</li>
     * </ul>
     */
    @Bean
    public NewTopic topicoPedidosEventos() {
        return TopicBuilder.name(topicoPedidos)
                .partitions(3)
                .replicas(1)
                .config("retention.ms",  "604800000")
                .config("cleanup.policy", "delete")
                .build();
    }

    /**
     * Tópico de auditoría de pedidos.
     * <ul>
     *   <li>2 particiones → volumen de auditoría menor que el de eventos</li>
     *   <li>1 réplica     → adecuado para entorno de desarrollo/single-broker</li>
     *   <li>retention.ms  → 24 horas (86 400 000 ms)</li>
     *   <li>cleanup.policy → delete</li>
     * </ul>
     */
    @Bean
    public NewTopic topicoAuditoriaEventos() {
        return TopicBuilder.name(topicoAuditoria)
                .partitions(2)
                .replicas(1)
                .config("retention.ms",  "86400000")
                .config("cleanup.policy", "delete")
                .build();
    }
}

