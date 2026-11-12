package com.pedidos360.pedidos.kafka.consumer;

import com.pedidos360.pedidos.kafka.dto.PedidoAuditoriaDto;
import com.pedidos360.pedidos.kafka.service.PedidoStreamingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
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
 *
 * <p>Si el payload es nulo o inválido se lanza {@link IllegalArgumentException},
 * lo que activa el {@code DefaultErrorHandler} → reintentos → DLT.</p>
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
     * @param particion partición Kafka de origen (inyectada desde la cabecera)
     * @param offset    offset del registro dentro de la partición
     */
    @KafkaListener(
            topics           = "${app.kafka.topico-auditoria}",
            groupId          = "pedidos-group-audit",
            containerFactory = "pedidoAuditoriaListenerFactory"
    )
    public void consumir(
            PedidoAuditoriaDto auditoria,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int particion,
            @Header(KafkaHeaders.OFFSET) long offset) {

        // Validación de payload: activa el DefaultErrorHandler si el mensaje está corrupto
        if (auditoria == null) {
            throw new IllegalArgumentException(
                    "[AUDITORIA] Payload nulo recibido en partición=" + particion + " offset=" + offset);
        }
        if (auditoria.pedidoId() == null || auditoria.pedidoId() <= 0) {
            throw new IllegalArgumentException(
                    "[AUDITORIA] pedidoId inválido '" + auditoria.pedidoId()
                            + "' en partición=" + particion + " offset=" + offset);
        }

        log.info("[Kafka][CONSUMER][AUDITORIA] pedidoId={} accion='{}' partición={} offset={}",
                auditoria.pedidoId(), auditoria.accion(), particion, offset);

        streamingService.procesarAuditoria(auditoria);
    }
}

