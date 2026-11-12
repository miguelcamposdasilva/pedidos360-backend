package com.pedidos360.pedidos.kafka.config;

import com.pedidos360.pedidos.kafka.dto.PedidoAuditoriaDto;
import com.pedidos360.pedidos.kafka.dto.PedidoEventoDto;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;

import java.util.HashMap;
import java.util.Map;

/**
 * Configuración del consumidor Kafka.
 *
 * <h3>Estrategia de deserialización dual</h3>
 * <p>Cada tópico tiene su propia {@link ConsumerFactory} tipada con el DTO
 * concreto que espera. Esto resuelve dos escenarios simultáneamente:</p>
 * <ul>
 *   <li><b>Productor Spring Kafka</b>: envía la cabecera {@code __TypeId__}
 *       → {@code USE_TYPE_INFO_HEADERS=true} la usa para inferir el tipo.</li>
 *   <li><b>Kafka-UI / mensaje manual</b>: no envía cabecera
 *       → {@code VALUE_DEFAULT_TYPE} actúa como fallback al DTO configurado.</li>
 * </ul>
 *
 * <p>{@link ErrorHandlingDeserializer} envuelve al {@link JsonDeserializer}
 * en ambas factories para que un mensaje corrupto vaya al DLT sin bloquear
 * la partición.</p>
 */
@Configuration
public class KafkaConsumerConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${spring.kafka.consumer.group-id}")
    private String defaultGroupId;

    // -------------------------------------------------------------------------
    // Props base comunes a ambas factories
    // -------------------------------------------------------------------------

    private Map<String, Object> baseProps() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");

        // ErrorHandlingDeserializer como capa externa
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG,
                ErrorHandlingDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
                ErrorHandlingDeserializer.class);

        // Delegate: String para clave, Json para valor
        props.put(ErrorHandlingDeserializer.KEY_DESERIALIZER_CLASS,
                StringDeserializer.class);
        props.put(ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS,
                JsonDeserializer.class);

        // Sin restricción de paquetes
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "*");

        // Usar __TypeId__ cuando existe (mensajes de Spring Kafka);
        // si no existe, se aplica VALUE_DEFAULT_TYPE como fallback (mensajes de Kafka-UI)
        props.put(JsonDeserializer.USE_TYPE_INFO_HEADERS, true);

        return props;
    }

    // -------------------------------------------------------------------------
    // Factory tipada para pedidos.eventos → PedidoEventoDto
    // -------------------------------------------------------------------------

    @Bean
    public ConsumerFactory<String, PedidoEventoDto> pedidoEventoConsumerFactory() {
        Map<String, Object> props = baseProps();
        props.put(ConsumerConfig.GROUP_ID_CONFIG, defaultGroupId);
        // Fallback cuando el mensaje llega sin cabecera __TypeId__ (ej. Kafka-UI)
        props.put(JsonDeserializer.VALUE_DEFAULT_TYPE,
                PedidoEventoDto.class.getName());
        return new DefaultKafkaConsumerFactory<>(props);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, PedidoEventoDto>
            pedidoEventoListenerFactory(DefaultErrorHandler kafkaErrorHandler) {

        ConcurrentKafkaListenerContainerFactory<String, PedidoEventoDto> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(pedidoEventoConsumerFactory());
        factory.setCommonErrorHandler(kafkaErrorHandler);
        return factory;
    }

    // -------------------------------------------------------------------------
    // Factory tipada para pedidos.auditoria → PedidoAuditoriaDto
    // -------------------------------------------------------------------------

    @Bean
    public ConsumerFactory<String, PedidoAuditoriaDto> pedidoAuditoriaConsumerFactory() {
        Map<String, Object> props = baseProps();
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "pedidos-group-audit");
        // Fallback cuando el mensaje llega sin cabecera __TypeId__ (ej. Kafka-UI)
        props.put(JsonDeserializer.VALUE_DEFAULT_TYPE,
                PedidoAuditoriaDto.class.getName());
        return new DefaultKafkaConsumerFactory<>(props);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, PedidoAuditoriaDto>
            pedidoAuditoriaListenerFactory(DefaultErrorHandler kafkaErrorHandler) {

        ConcurrentKafkaListenerContainerFactory<String, PedidoAuditoriaDto> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(pedidoAuditoriaConsumerFactory());
        factory.setCommonErrorHandler(kafkaErrorHandler);
        return factory;
    }
}

