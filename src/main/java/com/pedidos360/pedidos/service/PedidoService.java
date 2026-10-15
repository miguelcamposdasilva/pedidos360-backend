package com.pedidos360.pedidos.service;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.pedidos360.pedidos.dto.PedidoEvento;
import com.pedidos360.pedidos.dto.PedidoRequest;
import com.pedidos360.pedidos.model.EstadoPedido;
import com.pedidos360.pedidos.model.Pedido;
import com.pedidos360.pedidos.producer.pedido.PedidoProducer;
import com.pedidos360.pedidos.repository.PedidoRepository;

/**
 * Logica de negocio de los pedidos.
 *
 * El controlador solo traduce HTTP; el acceso a datos vive en el
 * repositorio. Esta capa concentra las reglas y los limites de
 * transaccion, de modo que la misma operacion pueda invocarse desde
 * otro punto de entrada sin duplicar codigo.
 */
@Service
@Transactional(readOnly = true)
public class PedidoService {

    private static final Logger log = LoggerFactory.getLogger(PedidoService.class);

    private final PedidoRepository pedidoRepository;
    private final PedidoProducer pedidoProducer;

    public PedidoService(PedidoRepository pedidoRepository, PedidoProducer pedidoProducer) {
        this.pedidoRepository = pedidoRepository;
        this.pedidoProducer = pedidoProducer;
    }

    public List<Pedido> listar() {
        return this.pedidoRepository.findAll();
    }

    public Pedido obtener(Long id) {
        return this.pedidoRepository.findById(id)
                .orElseThrow(() -> this.noEncontrado(id));
    }

    /**
     * Registra un pedido.
     *
     * @param solicitud datos enviados por el cliente
     * @param creadoPor identidad tomada del token, nunca del cuerpo de
     *                  la peticion: asi un usuario no puede registrar
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

        // Emite el evento asincrono hacia RabbitMQ sin alterar el retorno ni el contrato HTTP
        this.pedidoProducer.publicarEvento(PedidoEvento.de(guardado, "PEDIDO_CREADO"));

        return guardado;
    }

    @Transactional
    public void eliminar(Long id) {

        if (!this.pedidoRepository.existsById(id)) {
            throw this.noEncontrado(id);
        }

        this.pedidoRepository.deleteById(id);
    }

    /**
     * Procesa un evento de pedido recibido asincronamente desde RabbitMQ.
     * Mantiene las reglas de negocio encapsuladas en la capa de servicio,
     * completamente separadas de la infraestructura de mensajeria.
     *
     * @param evento datos del evento recibido
     */
    @Transactional
    public void procesarEvento(PedidoEvento evento) {

        if (evento == null) {
            throw new IllegalArgumentException("El evento de pedido no puede ser nulo");
        }

        log.info("Procesando evento asincrono RabbitMQ para pedido ID: {}, tipo: {}",
                evento.idPedido(), evento.tipoEvento());

        // Si referencia un pedido existente, se valida en base de datos.
        // Si no existe, lanzara ResponseStatusException (NOT_FOUND) provocando el NACK hacia la DLQ.
        if (evento.idPedido() != null) {
            Pedido pedido = this.obtener(evento.idPedido());

            // Simulacion de generacion de ticket/comprobante digital para la entrega
            log.info("[TICKET EMITIDO] Comprobante electronico #TK-{} emitido para cliente '{}'. Monto: ${}. Transicionando estado a PREPARANDO",
                    pedido.getId(), pedido.getCliente(), pedido.getMonto());

            // Actualiza el estado a PREPARANDO tras la emision del ticket
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
