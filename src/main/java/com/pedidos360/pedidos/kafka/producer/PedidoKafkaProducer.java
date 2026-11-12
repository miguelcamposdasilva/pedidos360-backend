package com.pedidos360.pedidos.kafka.producer;

import com.pedidos360.pedidos.kafka.dto.PedidoAuditoriaDto;
import com.pedidos360.pedidos.kafka.dto.PedidoEventoDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

/**
 * Publicador de eventos de pedidos en Apache Kafka.
 *
 * <p>Responsabilidad única: serializar los DTOs y enviarlos al broker.
 * No contiene lógica de negocio; es invocado exclusivamente desde
 * {@link com.pedidos360.pedidos.service.PedidoService}.</p>
 *
 * <p>La clave de cada mensaje es el {@code pedidoId} en formato String,
 * lo que garantiza que todos los eventos de un mismo pedido aterricen
 * en la misma partición y, por tanto, se consuman en orden.</p>
 */
@Service
public class PedidoKafkaProducer {

    private static final Logger log = LoggerFactory.getLogger(PedidoKafkaProducer.class);

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${app.kafka.topico-pedidos}")
    private String topicoPedidos;

    @Value("${app.kafka.topico-auditoria}")
    private String topicoAuditoria;

    public PedidoKafkaProducer(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    /**
     * Publica un {@link PedidoEventoDto} en el tópico de eventos de pedidos.
     *
     * @param evento DTO con los datos del evento a publicar
     */
    public void publicarEventoPedido(PedidoEventoDto evento) {
        String key = String.valueOf(evento.pedidoId());
        log.info("[Kafka][PRODUCER] Publicando evento tipo='{}' pedidoId={} en tópico='{}'",
                evento.tipoEvento(), evento.pedidoId(), topicoPedidos);

        kafkaTemplate.send(topicoPedidos, key, evento)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("[Kafka][PRODUCER] Error al publicar evento pedidoId={}: {}",
                                evento.pedidoId(), ex.getMessage(), ex);
                    } else {
                        log.debug("[Kafka][PRODUCER] Evento pedidoId={} confirmado en partición={} offset={}",
                                evento.pedidoId(),
                                result.getRecordMetadata().partition(),
                                result.getRecordMetadata().offset());
                    }
                });
    }

    /**
     * Publica un {@link PedidoAuditoriaDto} en el tópico de auditoría.
     *
     * @param auditoria DTO con los datos de auditoría a publicar
     */
    public void publicarAuditoria(PedidoAuditoriaDto auditoria) {
        String key = String.valueOf(auditoria.pedidoId());
        log.info("[Kafka][PRODUCER] Publicando auditoría accion='{}' pedidoId={} en tópico='{}'",
                auditoria.accion(), auditoria.pedidoId(), topicoAuditoria);

        kafkaTemplate.send(topicoAuditoria, key, auditoria)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("[Kafka][PRODUCER] Error al publicar auditoría pedidoId={}: {}",
                                auditoria.pedidoId(), ex.getMessage(), ex);
                    } else {
                        log.debug("[Kafka][PRODUCER] Auditoría pedidoId={} confirmada en partición={} offset={}",
                                auditoria.pedidoId(),
                                result.getRecordMetadata().partition(),
                                result.getRecordMetadata().offset());
                    }
                });
    }
}

