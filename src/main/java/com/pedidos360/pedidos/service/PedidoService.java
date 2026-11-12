package com.pedidos360.pedidos.service;

import java.time.Instant;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import com.pedidos360.pedidos.dto.PedidoRequest;
import com.pedidos360.pedidos.kafka.dto.PedidoAuditoriaDto;
import com.pedidos360.pedidos.kafka.dto.PedidoEventoDto;
import com.pedidos360.pedidos.kafka.producer.PedidoKafkaProducer;
import com.pedidos360.pedidos.model.Pedido;
import com.pedidos360.pedidos.repository.PedidoRepository;

/**
 * Lógica de negocio de los pedidos.
 *
 * El controlador solo traduce HTTP; el acceso a datos vive en el
 * repositorio. Esta capa concentra las reglas y los límites de
 * transacción, de modo que la misma operación pueda invocarse desde
 * otro punto de entrada sin duplicar código.
 *
 * <p>Tras cada operación de escritura exitosa se publican eventos en
 * Kafka a través de {@link PedidoKafkaProducer}: el envío es
 * asíncrono y no bloquea la respuesta HTTP.</p>
 */
@Service
@Transactional(readOnly = true)
public class PedidoService {

    private final PedidoRepository pedidoRepository;
    private final PedidoKafkaProducer kafkaProducer;

    public PedidoService(PedidoRepository pedidoRepository,
                         PedidoKafkaProducer kafkaProducer) {
        this.pedidoRepository = pedidoRepository;
        this.kafkaProducer    = kafkaProducer;
    }

    public List<Pedido> listar() {
        return this.pedidoRepository.findAll();
    }

    public Pedido obtener(Long id) {
        return this.pedidoRepository.findById(id)
                .orElseThrow(() -> this.noEncontrado(id));
    }

    /**
     * Registra un pedido y publica los eventos Kafka correspondientes.
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

        // --- Eventos Kafka (post-commit, no bloquean la respuesta HTTP) --------
        Instant ahora = Instant.now();

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

        return guardado;
    }

    /**
     * Elimina un pedido y publica los eventos Kafka correspondientes.
     *
     * @param id         identificador del pedido a eliminar
     * @param eliminadoPor identidad del usuario que realiza la eliminación
     */
    @Transactional
    public void eliminar(Long id, String eliminadoPor) {

        Pedido pedido = this.pedidoRepository.findById(id)
                .orElseThrow(() -> this.noEncontrado(id));

        this.pedidoRepository.deleteById(id);

        // --- Eventos Kafka (post-commit, no bloquean la respuesta HTTP) --------
        Instant ahora = Instant.now();

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

    private ResponseStatusException noEncontrado(Long id) {
        return new ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "No existe el pedido " + id);
    }
}
