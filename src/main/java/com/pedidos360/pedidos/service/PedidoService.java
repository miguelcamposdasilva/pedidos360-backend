package com.pedidos360.pedidos.service;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.pedidos360.pedidos.dto.PedidoRequest;
import com.pedidos360.pedidos.model.Pedido;
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

    private final PedidoRepository pedidoRepository;

    public PedidoService(PedidoRepository pedidoRepository) {
        this.pedidoRepository = pedidoRepository;
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

        return this.pedidoRepository.save(pedido);
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
    public void procesarEvento(com.pedidos360.pedidos.dto.PedidoEvento evento) {

        if (evento == null) {
            throw new IllegalArgumentException("El evento de pedido no puede ser nulo");
        }

        // Si referencia un pedido existente, se valida en base de datos.
        // Si no existe, lanzara ResponseStatusException (NOT_FOUND) provocando el NACK hacia la DLQ.
        if (evento.idPedido() != null) {
            Pedido pedido = this.obtener(evento.idPedido());

            if (evento.estado() != null) {
                pedido.setEstado(evento.estado());
                this.pedidoRepository.save(pedido);
            }
        }
    }

    private ResponseStatusException noEncontrado(Long id) {
        return new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "No existe el pedido " + id);
    }
}
