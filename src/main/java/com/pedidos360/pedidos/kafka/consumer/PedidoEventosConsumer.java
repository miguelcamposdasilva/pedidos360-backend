package com.pedidos360.pedidos.kafka.consumer;

import com.pedidos360.pedidos.kafka.dto.PedidoEventoDto;
import com.pedidos360.pedidos.kafka.service.PedidoStreamingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
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
     * @param evento DTO deserializado automáticamente por Spring Kafka + Jackson
     */
    @KafkaListener(
            topics   = "${app.kafka.topico-pedidos}",
            groupId  = "${spring.kafka.consumer.group-id}",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumir(PedidoEventoDto evento) {
        log.info("[Kafka][CONSUMER][EVENTOS] Mensaje recibido pedidoId={} tipo='{}'",
                evento.pedidoId(), evento.tipoEvento());
        streamingService.procesarEventoPedido(evento);
    }
}

