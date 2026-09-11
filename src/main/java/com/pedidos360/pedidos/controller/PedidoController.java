package com.pedidos360.pedidos.controller;

import java.net.URI;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.pedidos360.pedidos.dto.PedidoRequest;
import com.pedidos360.pedidos.model.Pedido;
import com.pedidos360.pedidos.service.PedidoService;

import jakarta.validation.Valid;

/**
 * Endpoints REST de pedidos.
 *
 * El controlador solo traduce HTTP: delega las reglas de negocio en
 * PedidoService. Las reglas de autorizacion por rol estan declaradas
 * de forma centralizada en SecurityConfig.
 */
@RestController
@RequestMapping("/api/pedidos")
public class PedidoController {

    private final PedidoService pedidoService;

    public PedidoController(PedidoService pedidoService) {
        this.pedidoService = pedidoService;
    }

    /** Requiere rol Pedidos.Lector o Pedidos.Admin. */
    @GetMapping
    public List<Pedido> listar() {
        return this.pedidoService.listar();
    }

    @GetMapping("/{id}")
    public Pedido obtener(@PathVariable Long id) {
        return this.pedidoService.obtener(id);
    }

    /** Requiere rol Pedidos.Admin: un usuario solo Lector recibe 403. */
    @PostMapping
    public ResponseEntity<Pedido> crear(
            @Valid @RequestBody PedidoRequest solicitud,
            @AuthenticationPrincipal Jwt jwt) {

        Pedido guardado =
                this.pedidoService.crear(solicitud, this.correoDelToken(jwt));

        return ResponseEntity
                .created(URI.create("/api/pedidos/" + guardado.getId()))
                .body(guardado);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {

        this.pedidoService.eliminar(id);

        return ResponseEntity.noContent().build();
    }

    /**
     * La identidad se toma del token, nunca del cuerpo de la peticion.
     */
    private String correoDelToken(Jwt jwt) {

        String correo = jwt.getClaimAsString("preferred_username");

        if (correo == null) {
            correo = jwt.getClaimAsString("upn");
        }

        return correo != null ? correo : jwt.getSubject();
    }
}
