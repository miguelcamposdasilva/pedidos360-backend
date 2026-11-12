package com.pedidos360.pedidos.kafka.service;

import com.pedidos360.pedidos.kafka.dto.PedidoAuditoriaDto;
import com.pedidos360.pedidos.kafka.dto.PedidoEventoDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Servicio de procesamiento analítico y de auditoría de eventos Kafka.
 *
 * <p>Mantiene estricta separación entre la infraestructura de mensajería
 * (consumidores en {@code kafka.consumer}) y la lógica de procesamiento:
 * los consumidores solo deserializan y delegan; esta clase decide qué
 * hacer con cada mensaje recibido.</p>
 *
 * <p>En esta etapa registra los eventos por log estructurado. Futuras
 * iteraciones pueden persistir métricas, actualizar proyecciones CQRS,
 * disparar alertas o llamar a servicios externos sin tocar los consumers.</p>
 */
@Service
public class PedidoStreamingService {

    private static final Logger log = LoggerFactory.getLogger(PedidoStreamingService.class);

    /**
     * Procesa un evento de pedido recibido desde el tópico {@code pedidos.eventos}.
     *
     * @param evento DTO deserializado con los datos del evento
     */
    public void procesarEventoPedido(PedidoEventoDto evento) {
        log.info("[Streaming][EVENTO] tipo='{}' pedidoId={} cliente='{}' monto={} estado='{}' creadoPor='{}'",
                evento.tipoEvento(),
                evento.pedidoId(),
                evento.cliente(),
                evento.monto(),
                evento.estado(),
                evento.creadoPor());

        // Punto de extensión: aquí se puede agregar lógica analítica,
        // actualización de proyecciones, notificaciones, etc.
        switch (evento.tipoEvento()) {
            case "CREADO"    -> log.debug("[Streaming] Nuevo pedido registrado en el pipeline analítico: id={}", evento.pedidoId());
            case "ELIMINADO" -> log.debug("[Streaming] Pedido eliminado del pipeline analítico: id={}", evento.pedidoId());
            default          -> log.warn("[Streaming] Tipo de evento desconocido '{}' para pedidoId={}", evento.tipoEvento(), evento.pedidoId());
        }
    }

    /**
     * Procesa un registro de auditoría recibido desde el tópico {@code pedidos.auditoria}.
     *
     * @param auditoria DTO deserializado con los datos de auditoría
     */
    public void procesarAuditoria(PedidoAuditoriaDto auditoria) {
        log.info("[Streaming][AUDITORIA] accion='{}' pedidoId={} realizadoPor='{}' detalle='{}'",
                auditoria.accion(),
                auditoria.pedidoId(),
                auditoria.realizadoPor(),
                auditoria.detalle());

        // Punto de extensión: persistir en tabla de auditoría,
        // enviar a SIEM, generar alertas de compliance, etc.
    }
}

