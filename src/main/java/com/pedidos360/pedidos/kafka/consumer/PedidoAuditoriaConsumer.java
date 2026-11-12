package com.pedidos360.pedidos.kafka.consumer;

import com.pedidos360.pedidos.kafka.dto.PedidoAuditoriaDto;
import com.pedidos360.pedidos.kafka.service.PedidoStreamingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumidor del tópico de auditoría de pedidos ({@code pedidos.auditoria}).
 *
 * <p>Responsabilidad única: recibir y deserializar el mensaje Kafka y
 * delegar el procesamiento a {@link PedidoStreamingService}. No contiene
 * lógica de negocio ni acceso a base de datos.</p>
 *
 * <p>Usa el grupo {@code pedidos-group-audit}, separado del grupo de
 * analítica, de modo que ambos pipelines consuman independientemente
 * los mismos tópicos sin interferencia.</p>
 */
@Component
public class PedidoAuditoriaConsumer {

    private static final Logger log = LoggerFactory.getLogger(PedidoAuditoriaConsumer.class);

    private final PedidoStreamingService streamingService;

    public PedidoAuditoriaConsumer(PedidoStreamingService streamingService) {
        this.streamingService = streamingService;
    }

    /**
     * Escucha mensajes del tópico de auditoría.
     *
     * @param auditoria DTO deserializado automáticamente por Spring Kafka + Jackson
     */
    @KafkaListener(
            topics   = "${app.kafka.topico-auditoria}",
            groupId  = "pedidos-group-audit",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumir(PedidoAuditoriaDto auditoria) {
        log.info("[Kafka][CONSUMER][AUDITORIA] Mensaje recibido pedidoId={} accion='{}'",
                auditoria.pedidoId(), auditoria.accion());
        streamingService.procesarAuditoria(auditoria);
    }
}

