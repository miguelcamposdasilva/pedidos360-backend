package com.pedidos360.pedidos.kafka.admin;

import com.pedidos360.pedidos.kafka.admin.dto.CrearTopicoRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.Map;
import java.util.Set;

/**
 * Controlador REST de administración de tópicos Kafka.
 *
 * <p>Todas las rutas bajo {@code /api/kafka} requieren el rol
 * {@code Pedidos.Admin}, declarado de forma centralizada en
 * {@link com.pedidos360.pedidos.config.SecurityConfig}.</p>
 *
 * <p>Este controlador solo traduce HTTP: delega la lógica de
 * administración en {@link KafkaAdminService}.</p>
 *
 * <p>Los errores de validación ({@code @Valid}) son capturados por
 * {@code ManejadorDeErrores} y devuelven un 400 con estructura uniforme.
 * Los errores de negocio ({@link IllegalArgumentException}) son 400;
 * los errores de broker ({@link RuntimeException}) son 500.</p>
 */
@RestController
@RequestMapping("/api/kafka")
public class KafkaAdminController {

    private final KafkaAdminService adminService;

    public KafkaAdminController(KafkaAdminService adminService) {
        this.adminService = adminService;
    }

    /**
     * Lista todos los tópicos existentes en el broker.
     *
     * @return 200 OK con el conjunto de nombres de tópicos
     */
    @GetMapping("/topicos")
    public ResponseEntity<Set<String>> listarTopicos() {
        return ResponseEntity.ok(adminService.listarTopicos());
    }

    /**
     * Devuelve los metadatos de un tópico específico.
     *
     * @param nombre nombre del tópico a inspeccionar
     * @return 200 OK con {@code nombre}, {@code particiones}, {@code replicacion} e {@code interno}
     */
    @GetMapping("/topicos/{nombre}")
    public ResponseEntity<Map<String, Object>> inspeccionarTopico(@PathVariable String nombre) {
        return ResponseEntity.ok(adminService.inspeccionarTopico(nombre));
    }

    /**
     * Crea un nuevo tópico en el broker.
     *
     * @param request datos del tópico (nombre, particiones, replicación)
     * @return 201 Created con {@code Location: /api/kafka/topicos/{nombre}}
     */
    @PostMapping("/topicos")
    public ResponseEntity<Void> crearTopico(@Valid @RequestBody CrearTopicoRequest request) {
        adminService.crearTopico(request);
        return ResponseEntity
                .created(URI.create("/api/kafka/topicos/" + request.nombre()))
                .build();
    }

    /**
     * Elimina un tópico del broker.
     *
     * @param nombre nombre del tópico a eliminar (los internos con prefijo {@code __} son rechazados)
     * @return 204 No Content
     */
    @DeleteMapping("/topicos/{nombre}")
    public ResponseEntity<Void> eliminarTopico(@PathVariable String nombre) {
        adminService.eliminarTopico(nombre);
        return ResponseEntity.noContent().build();
    }
}

