package com.pedidos360.pedidos.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.pedidos360.pedidos.model.EstadoPedido;
import com.pedidos360.pedidos.model.Pedido;

public interface PedidoRepository extends JpaRepository<Pedido, Long> {

    List<Pedido> findByEstadoOrderByCreadoEnDesc(EstadoPedido estado);

    List<Pedido> findByCreadoPorOrderByCreadoEnDesc(String creadoPor);
}
