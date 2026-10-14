package com.pedidos360.pedidos.admin.controller;

import java.net.URI;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.pedidos360.pedidos.admin.dto.BindingRequestDto;
import com.pedidos360.pedidos.admin.dto.ExchangeRequestDto;
import com.pedidos360.pedidos.admin.dto.QueueRequestDto;
import com.pedidos360.pedidos.admin.service.RabbitAdminService;

import jakarta.validation.Valid;

/**
 * Controlador REST para la administracion de topologia de RabbitMQ.
 *
 * Expone endpoints estandar para crear y eliminar colas, exchanges y bindings.
 * La logica de infraestructura esta 100% delegada en RabbitAdminService.
 * Soporta tanto el prefijo /admin como rutas directas (/queues, /exchanges, /bindings).
 */
@RestController
@RequestMapping({"/admin", ""})
public class RabbitAdminController {

    private final RabbitAdminService adminService;

    public RabbitAdminController(RabbitAdminService adminService) {
        this.adminService = adminService;
    }

    /**
     * Endpoint POST /admin/queues y POST /queues.
     * Crea una nueva cola en RabbitMQ validando los parametros con Jakarta Bean Validation.
     *
     * @param request DTO validado
     * @return 201 Created con detalles de la cola creada
     */
    @PostMapping("/queues")
    public ResponseEntity<Map<String, Object>> crearCola(@Valid @RequestBody QueueRequestDto request) {
        String nombreCola = this.adminService.crearCola(request);
        URI ubicacion = URI.create("/admin/queues/" + nombreCola);

        return ResponseEntity.created(ubicacion).body(Map.of(
                "mensaje", "Cola creada exitosamente en RabbitMQ",
                "queue", nombreCola,
                "durable", request.durable(),
                "exclusive", request.exclusive(),
                "autoDelete", request.autoDelete()
        ));
    }

    /**
     * Endpoint DELETE /admin/queues/{name} y DELETE /queues/{name}.
     * Elimina una cola existente en RabbitMQ.
     *
     * @param name nombre de la cola
     * @return 200 OK informando el resultado
     */
    @DeleteMapping("/queues/{name}")
    public ResponseEntity<Map<String, Object>> eliminarCola(@PathVariable String name) {
        boolean eliminada = this.adminService.eliminarCola(name);

        return ResponseEntity.ok(Map.of(
                "mensaje", eliminada ? "Cola eliminada exitosamente" : "La cola no existia o no pudo ser eliminada",
                "queue", name,
                "eliminada", eliminada
        ));
    }

    /**
     * Endpoint POST /admin/exchanges y POST /exchanges.
     * Crea un exchange en RabbitMQ validando nombre y tipo.
     *
     * @param request DTO validado
     * @return 201 Created con detalles del exchange
     */
    @PostMapping("/exchanges")
    public ResponseEntity<Map<String, Object>> crearExchange(@Valid @RequestBody ExchangeRequestDto request) {
        this.adminService.crearExchange(request);
        URI ubicacion = URI.create("/admin/exchanges/" + request.name());

        return ResponseEntity.created(ubicacion).body(Map.of(
                "mensaje", "Exchange creado exitosamente en RabbitMQ",
                "exchange", request.name(),
                "type", request.type().toLowerCase(),
                "durable", request.durable(),
                "autoDelete", request.autoDelete()
        ));
    }

    /**
     * Endpoint DELETE /admin/exchanges/{name} y DELETE /exchanges/{name}.
     * Elimina un exchange de RabbitMQ.
     *
     * @param name nombre del exchange
     * @return 200 OK informando el resultado
     */
    @DeleteMapping("/exchanges/{name}")
    public ResponseEntity<Map<String, Object>> eliminarExchange(@PathVariable String name) {
        boolean eliminado = this.adminService.eliminarExchange(name);

        return ResponseEntity.ok(Map.of(
                "mensaje", eliminado ? "Exchange eliminado exitosamente" : "El exchange no existia o no pudo ser eliminado",
                "exchange", name,
                "eliminado", eliminado
        ));
    }

    /**
     * Endpoint POST /admin/bindings y POST /bindings.
     * Asocia una cola con un exchange mediante una routing key.
     *
     * @param request DTO validado
     * @return 201 Created con la confirmacion del binding
     */
    @PostMapping("/bindings")
    public ResponseEntity<Map<String, Object>> crearBinding(@Valid @RequestBody BindingRequestDto request) {
        this.adminService.crearBinding(request);

        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "mensaje", "Binding creado exitosamente en RabbitMQ",
                "queue", request.queue(),
                "exchange", request.exchange(),
                "routingKey", request.routingKey()
        ));
    }

    /**
     * Endpoint DELETE /admin/bindings y DELETE /bindings.
     * Elimina una asociacion (binding) existente.
     *
     * @param request DTO validado
     * @return 200 OK informando la eliminacion
     */
    @DeleteMapping("/bindings")
    public ResponseEntity<Map<String, Object>> eliminarBinding(@Valid @RequestBody BindingRequestDto request) {
        this.adminService.eliminarBinding(request);

        return ResponseEntity.ok(Map.of(
                "mensaje", "Binding eliminado exitosamente en RabbitMQ",
                "queue", request.queue(),
                "exchange", request.exchange(),
                "routingKey", request.routingKey()
        ));
    }
}
