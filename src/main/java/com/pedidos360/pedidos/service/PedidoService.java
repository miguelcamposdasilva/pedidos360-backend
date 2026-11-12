package com.pedidos360.pedidos.service;

import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.pedidos360.pedidos.dto.PedidoEvento;
import com.pedidos360.pedidos.dto.PedidoRequest;
import com.pedidos360.pedidos.kafka.dto.PedidoAuditoriaDto;
import com.pedidos360.pedidos.kafka.dto.PedidoEventoDto;
import com.pedidos360.pedidos.kafka.producer.PedidoKafkaProducer;
import com.pedidos360.pedidos.model.EstadoPedido;
import com.pedidos360.pedidos.model.Pedido;
import com.pedidos360.pedidos.producer.pedido.PedidoProducer;
import com.pedidos360.pedidos.repository.PedidoRepository;

/**
 * Lógica de negocio de los pedidos.
 *
 * <p>El controlador solo traduce HTTP; el acceso a datos vive en el
 * repositorio. Esta capa concentra las reglas y los límites de
 * transacción, de modo que la misma operación pueda invocarse desde
 * otro punto de entrada sin duplicar código.</p>
 *
 * <p>Tras cada operación de escritura exitosa se publican eventos en
 * <b>Kafka</b> (vía {@link PedidoKafkaProducer}) y en
 * <b>RabbitMQ</b> (vía {@link PedidoProducer}): ambos envíos son
 * asíncronos y no bloquean la respuesta HTTP.</p>
 */
@Service
@Transactional(readOnly = true)
public class PedidoService {

    private static final Logger log = LoggerFactory.getLogger(PedidoService.class);

    private final PedidoRepository  pedidoRepository;
    private final PedidoKafkaProducer kafkaProducer;
    private final PedidoProducer     pedidoProducer;

    public PedidoService(PedidoRepository pedidoRepository,
                         PedidoKafkaProducer kafkaProducer,
                         PedidoProducer pedidoProducer) {
        this.pedidoRepository = pedidoRepository;
        this.kafkaProducer    = kafkaProducer;
        this.pedidoProducer   = pedidoProducer;
    }

    public List<Pedido> listar() {
        return this.pedidoRepository.findAll();
    }

    public Pedido obtener(Long id) {
        return this.pedidoRepository.findById(id)
                .orElseThrow(() -> this.noEncontrado(id));
    }

    /**
     * Registra un pedido y publica los eventos correspondientes en Kafka y RabbitMQ.
     *
     * @param solicitud datos enviados por el cliente
     * @param creadoPor identidad tomada del token, nunca del cuerpo de
     *                  la petición: así un usuario no puede registrar
     *                  pedidos a nombre de otro
     */
    @Transactional
    public Pedido crear(PedidoRequest solicitud, String creadoPor) {

        Pedido pedido = new Pedido();
        pedido.setCliente(solicitud.cliente());
        pedido.setDescripcion(solicitud.descripcion());
        pedido.setMonto(solicitud.monto());
        pedido.setCreadoPor(creadoPor);

        Pedido guardado = this.pedidoRepository.save(pedido);

        Instant ahora = Instant.now();

        // --- Kafka: evento de negocio + auditoría ---------------------------
        kafkaProducer.publicarEventoPedido(new PedidoEventoDto(
                guardado.getId(),
                guardado.getCliente(),
                guardado.getDescripcion(),
                guardado.getMonto(),
                guardado.getEstado().name(),
                guardado.getCreadoPor(),
                ahora,
                "CREADO"
        ));

        kafkaProducer.publicarAuditoria(new PedidoAuditoriaDto(
                guardado.getId(),
                "CREAR_PEDIDO",
                guardado.getCreadoPor(),
                ahora,
                "Pedido creado por " + guardado.getCreadoPor()
                        + " para el cliente '" + guardado.getCliente() + "'"
        ));

        // --- RabbitMQ: evento asíncrono de notificación ---------------------
        this.pedidoProducer.publicarEvento(PedidoEvento.de(guardado, "PEDIDO_CREADO"));

        return guardado;
    }

    /**
     * Elimina un pedido y publica los eventos Kafka correspondientes.
     *
     * @param id           identificador del pedido a eliminar
     * @param eliminadoPor identidad del usuario que realiza la eliminación
     */
    @Transactional
    public void eliminar(Long id, String eliminadoPor) {

        Pedido pedido = this.pedidoRepository.findById(id)
                .orElseThrow(() -> this.noEncontrado(id));

        this.pedidoRepository.deleteById(id);

        Instant ahora = Instant.now();

        // --- Kafka: evento de negocio + auditoría ---------------------------
        kafkaProducer.publicarEventoPedido(new PedidoEventoDto(
                pedido.getId(),
                pedido.getCliente(),
                pedido.getDescripcion(),
                pedido.getMonto(),
                pedido.getEstado().name(),
                pedido.getCreadoPor(),
                ahora,
                "ELIMINADO"
        ));

        kafkaProducer.publicarAuditoria(new PedidoAuditoriaDto(
                pedido.getId(),
                "ELIMINAR_PEDIDO",
                eliminadoPor,
                ahora,
                "Pedido " + id + " eliminado por " + eliminadoPor
        ));
    }

    /**
     * Procesa un evento de pedido recibido asincrónamente desde RabbitMQ.
     * Mantiene las reglas de negocio encapsuladas en la capa de servicio,
     * completamente separadas de la infraestructura de mensajería.
     *
     * @param evento datos del evento recibido
     */
    @Transactional
    public void procesarEvento(PedidoEvento evento) {

        if (evento == null) {
            throw new IllegalArgumentException("El evento de pedido no puede ser nulo");
        }

        log.info("Procesando evento asíncrono RabbitMQ para pedido ID: {}, tipo: {}",
                evento.idPedido(), evento.tipoEvento());

        // Si referencia un pedido existente, se valida en base de datos.
        // Si no existe, lanzará ResponseStatusException (NOT_FOUND) provocando el NACK hacia la DLQ.
        if (evento.idPedido() != null) {
            Pedido pedido = this.obtener(evento.idPedido());

            // Simulación de generación de ticket/comprobante digital para la entrega
            log.info("[TICKET EMITIDO] Comprobante electrónico #TK-{} emitido para cliente '{}'. Monto: ${}. Transicionando estado a PREPARANDO",
                    pedido.getId(), pedido.getCliente(), pedido.getMonto());

            // Actualiza el estado a PREPARANDO tras la emisión del ticket
            pedido.setEstado(EstadoPedido.PREPARANDO);
            this.pedidoRepository.save(pedido);

            log.info("Pedido ID {} actualizado a estado: {}", pedido.getId(), pedido.getEstado());
        }
    }

    private ResponseStatusException noEncontrado(Long id) {
        return new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "No existe el pedido " + id);
    }
}
