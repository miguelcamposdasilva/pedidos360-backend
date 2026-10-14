package com.pedidos360.pedidos.exception;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Respuestas JSON uniformes ante solicitudes invalidas.
 * Los 401 y 403 los produce Spring Security antes de llegar aqui.
 */
@RestControllerAdvice
public class ManejadorDeErrores {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> datosInvalidos(
            MethodArgumentNotValidException excepcion) {

        Map<String, String> errores = new HashMap<>();

        excepcion.getBindingResult().getFieldErrors().forEach(error ->
                errores.put(error.getField(), error.getDefaultMessage()));

        Map<String, Object> cuerpo = Map.of(
                "timestamp", Instant.now().toString(),
                "status", 400,
                "error", "Bad Request",
                "mensaje", "La solicitud contiene datos invalidos",
                "campos", errores);

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cuerpo);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> argumentoInvalido(
            IllegalArgumentException excepcion) {

        Map<String, Object> cuerpo = Map.of(
                "timestamp", Instant.now().toString(),
                "status", 400,
                "error", "Bad Request",
                "mensaje", excepcion.getMessage());

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(cuerpo);
    }
}
