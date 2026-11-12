package com.pedidos360.pedidos.kafka.consumer;

import com.pedidos360.pedidos.kafka.dto.PedidoEventoDto;
import com.pedidos360.pedidos.kafka.service.PedidoStreamingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

/**
 * Consumidor del tópico de eventos de pedidos ({@code pedidos.eventos}).
 *
 * <p>Responsabilidad única: recibir y deserializar el mensaje Kafka y
 * delegar el procesamiento a {@link PedidoStreamingService}. No contiene
 * lógica de negocio ni acceso a base de datos.</p>
 *
 * <p>El {@code groupId} {@code pedidos-group-analytics} permite escalar
 * horizontalmente: múltiples instancias del servicio consumirán
 * particiones distintas en paralelo sin procesar el mismo mensaje dos veces.</p>
 *
 * <p>Si el payload es nulo o inválido se lanza {@link IllegalArgumentException},
 * lo que activa el {@code DefaultErrorHandler} → reintentos → DLT.</p>
 */
@Component
public class PedidoEventosConsumer {

    private static final Logger log = LoggerFactory.getLogger(PedidoEventosConsumer.class);

    private final PedidoStreamingService streamingService;

    public PedidoEventosConsumer(PedidoStreamingService streamingService) {
        this.streamingService = streamingService;
    }

    /**
     * Escucha mensajes del tópico de eventos de pedidos.
     *
     * @param evento    DTO deserializado automáticamente por Spring Kafka + Jackson
     * @param particion partición Kafka de origen (inyectada desde la cabecera)
     * @param offset    offset del registro dentro de la partición
     */
    @KafkaListener(
            topics           = "${app.kafka.topico-pedidos}",
            groupId          = "${spring.kafka.consumer.group-id}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumir(
            PedidoEventoDto evento,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int particion,
            @Header(KafkaHeaders.OFFSET) long offset) {

        // Validación de payload: activa el DefaultErrorHandler si el mensaje está corrupto
        if (evento == null) {
            throw new IllegalArgumentException(
                    "[EVENTOS] Payload nulo recibido en partición=" + particion + " offset=" + offset);
        }
        if (evento.pedidoId() == null || evento.pedidoId() <= 0) {
            throw new IllegalArgumentException(
                    "[EVENTOS] pedidoId inválido '" + evento.pedidoId()
                            + "' en partición=" + particion + " offset=" + offset);
        }

        log.info("[Kafka][CONSUMER][EVENTOS] pedidoId={} tipo='{}' partición={} offset={}",
                evento.pedidoId(), evento.tipoEvento(), particion, offset);

        streamingService.procesarEventoPedido(evento);
    }
}

