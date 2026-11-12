package com.pedidos360.pedidos.kafka.config;

import com.pedidos360.pedidos.kafka.dto.PedidoEventoDto;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.TopicPartition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.RetryListener;
import org.springframework.util.backoff.FixedBackOff;

/**
 * Configuración de resiliencia y manejo de errores para los consumidores Kafka.
 *
 * <p><b>Estrategia de reintentos:</b> {@link FixedBackOff} con 2 reintentos
 * separados por 2 segundos cada uno. Si los 3 intentos fallan (1 original + 2
 * reintentos), el mensaje se envía automáticamente al tópico DLT
 * ({@code <tópico>.DLT}) mediante {@link DeadLetterPublishingRecoverer}.</p>
 *
 * <p><b>Dead Letter Topic (DLT):</b> garantiza que un mensaje envenenado
 * (poison pill) no bloquee indefinidamente una partición. El DLT puede ser
 * monitoreado en Kafka-UI y reprocesado manualmente.</p>
 *
 * <p><b>RetryListener:</b> registra cada intento fallido con metadatos del
 * registro Kafka para facilitar el diagnóstico en logs.</p>
 */
@Configuration
public class KafkaErrorConfig {

    private static final Logger log = LoggerFactory.getLogger(KafkaErrorConfig.class);

    /** 2 segundos entre reintentos. */
    private static final long BACKOFF_INTERVAL_MS = 2_000L;

    /** Número máximo de reintentos tras el intento original. */
    private static final long MAX_RETRIES = 2L;

    @Value("${app.kafka.topico-pedidos}")
    private String topicoPedidos;

    /**
     * {@link DefaultErrorHandler} compartido por todos los
     * {@code kafkaListenerContainerFactory} del servicio.
     *
     * <p>El {@link DeadLetterPublishingRecoverer} publica en
     * {@code <tópico-original>.DLT} conservando la misma partición
     * de origen para facilitar la correlación durante el diagnóstico.</p>
     *
     * @param kafkaTemplate template ya configurado por Spring Boot autoconfigure
     */
    @Bean
    public DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<String, Object> kafkaTemplate) {

        // Recoverer: envía el mensaje al DLT manteniendo la partición de origen
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
                kafkaTemplate,
                (record, ex) -> {
                    log.error("[Kafka][DLT] Mensaje enviado al DLT después de agotar reintentos. "
                                    + "tópico='{}' partición={} offset={} causa='{}'",
                            record.topic(),
                            record.partition(),
                            record.offset(),
                            ex.getMessage());
                    return new TopicPartition(record.topic() + ".DLT", record.partition());
                }
        );

        // FixedBackOff: 2 reintentos con 2 s de intervalo
        FixedBackOff backOff = new FixedBackOff(BACKOFF_INTERVAL_MS, MAX_RETRIES);

        DefaultErrorHandler errorHandler = new DefaultErrorHandler(recoverer, backOff);

        // RetryListener: loguea cada intento fallido con contexto completo
        errorHandler.setRetryListeners(new RetryListener() {
            @Override
            public void failedDelivery(org.apache.kafka.clients.consumer.ConsumerRecord<?, ?> record,
                                       Exception ex,
                                       int deliveryAttempt) {
                log.warn("[Kafka][RETRY] Intento {}/{} fallido. "
                                + "tópico='{}' partición={} offset={} causa='{}'",
                        deliveryAttempt,
                        MAX_RETRIES + 1,
                        record.topic(),
                        record.partition(),
                        record.offset(),
                        ex.getMessage());
            }

            @Override
            public void recovered(org.apache.kafka.clients.consumer.ConsumerRecord<?, ?> record,
                                  Exception ex) {
                log.info("[Kafka][RETRY] Mensaje recuperado (enviado a DLT). "
                                + "tópico='{}' partición={} offset={}",
                        record.topic(),
                        record.partition(),
                        record.offset());
            }

            @Override
            public void recoveryFailed(org.apache.kafka.clients.consumer.ConsumerRecord<?, ?> record,
                                       Exception original,
                                       Exception failure) {
                log.error("[Kafka][RETRY] Fallo también al publicar en DLT. "
                                + "tópico='{}' partición={} offset={} causaOriginal='{}' causaDLT='{}'",
                        record.topic(),
                        record.partition(),
                        record.offset(),
                        original.getMessage(),
                        failure.getMessage());
            }
        });

        // No reintentar errores de deserialización — ya los maneja ErrorHandlingDeserializer
        errorHandler.addNotRetryableExceptions(org.springframework.kafka.support.serializer.DeserializationException.class);

        return errorHandler;
    }

    /**
     * Tópico DLT para {@code pedidos.eventos}.
     * Creado con 1 partición y 1 réplica; se mostrará automáticamente en Kafka-UI.
     */
    @Bean
    public NewTopic topicoPedidosDlt() {
        return TopicBuilder.name(topicoPedidos + ".DLT")
                .partitions(1)
                .replicas(1)
                .build();
    }
}

