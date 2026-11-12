package com.pedidos360.pedidos.exception;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

/**
 * Respuestas JSON uniformes ante solicitudes inválidas o errores de negocio.
 * Los 401 y 403 los produce Spring Security antes de llegar aquí.
 *
 * <p>Formato de respuesta:</p>
 * <pre>
 * {
 *   "timestamp": "2026-...",
 *   "status": 400,
 *   "error": "Bad Request",
 *   "mensaje": "...",
 *   ["campos": { ... }]   // solo en errores de validación
 * }
 * </pre>
 */
@RestControllerAdvice
public class ManejadorDeErrores {

    // -------------------------------------------------------------------------
    // Bean Validation (@Valid) → 400 con detalle por campo
    // -------------------------------------------------------------------------

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> datosInvalidos(
            MethodArgumentNotValidException excepcion) {

        Map<String, String> errores = new HashMap<>();

        excepcion.getBindingResult().getFieldErrors().forEach(error ->
                errores.put(error.getField(), error.getDefaultMessage()));

        Map<String, Object> cuerpo = Map.of(
                "timestamp", Instant.now().toString(),
                "status",    400,
                "error",     "Bad Request",
                "mensaje",   "La solicitud contiene datos inválidos",
                "campos",    errores);

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cuerpo);
    }

    // -------------------------------------------------------------------------
    // IllegalArgumentException → 400 (ej. tópico interno, pedidoId inválido)
    // -------------------------------------------------------------------------

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> argumentoInvalido(
            IllegalArgumentException excepcion) {

        Map<String, Object> cuerpo = Map.of(
                "timestamp", Instant.now().toString(),
                "status",    400,
                "error",     "Bad Request",
                "mensaje",   excepcion.getMessage());

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cuerpo);
    }

    // -------------------------------------------------------------------------
    // ResponseStatusException → código HTTP del propio throw (ej. 404 de PedidoService)
    // -------------------------------------------------------------------------

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> responseStatus(
            ResponseStatusException excepcion) {

        Map<String, Object> cuerpo = Map.of(
                "timestamp", Instant.now().toString(),
                "status",    excepcion.getStatusCode().value(),
                "error",     excepcion.getStatusCode().toString(),
                "mensaje",   excepcion.getReason() != null
                        ? excepcion.getReason()
                        : excepcion.getMessage());

        return ResponseEntity.status(excepcion.getStatusCode()).body(cuerpo);
    }

    // -------------------------------------------------------------------------
    // RuntimeException de broker Kafka → 503 Service Unavailable
    // -------------------------------------------------------------------------

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<Map<String, Object>> errorBroker(RuntimeException excepcion) {

        Map<String, Object> cuerpo = Map.of(
                "timestamp", Instant.now().toString(),
                "status",    503,
                "error",     "Service Unavailable",
                "mensaje",   "Error al comunicarse con el broker Kafka: "
                        + excepcion.getMessage());

        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(cuerpo);
    }
}

