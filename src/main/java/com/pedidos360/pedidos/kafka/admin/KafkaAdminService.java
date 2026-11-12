package com.pedidos360.pedidos.kafka.admin;

import com.pedidos360.pedidos.kafka.admin.dto.CrearTopicoRequest;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.admin.TopicDescription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;

/**
 * Servicio de administración dinámica de tópicos Kafka.
 *
 * <p>Utiliza {@link AdminClient} vía {@link KafkaAdmin} para realizar
 * operaciones de gestión. Cada método abre y cierra su propio
 * {@code AdminClient} con {@code try-with-resources} para liberar
 * conexiones correctamente.</p>
 *
 * <p>Los tópicos internos de Kafka (prefijo {@code __}) están protegidos
 * contra eliminación accidental.</p>
 */
@Service
public class KafkaAdminService {

    private static final Logger log = LoggerFactory.getLogger(KafkaAdminService.class);

    /** Prefijo que identifica tópicos internos del broker (ej. {@code __consumer_offsets}). */
    private static final String PREFIJO_INTERNO = "__";

    private final KafkaAdmin kafkaAdmin;

    public KafkaAdminService(KafkaAdmin kafkaAdmin) {
        this.kafkaAdmin = kafkaAdmin;
    }

    /**
     * Lista todos los tópicos existentes en el broker, marcando los internos.
     *
     * @return conjunto de nombres de tópicos (internos incluidos, identificados con prefijo {@code __})
     * @throws RuntimeException si la operación con el broker falla
     */
    public Set<String> listarTopicos() {
        log.info("[KafkaAdmin] Listando todos los tópicos del broker");
        try (AdminClient client = AdminClient.create(kafkaAdmin.getConfigurationProperties())) {
            Set<String> topicos = client.listTopics().names().get();
            log.debug("[KafkaAdmin] Tópicos encontrados: {}", topicos.size());
            return topicos;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Operación interrumpida al listar tópicos", e);
        } catch (ExecutionException e) {
            throw new RuntimeException("Error al listar tópicos del broker: " + e.getCause().getMessage(), e);
        }
    }

    /**
     * Inspecciona un tópico específico y devuelve sus metadatos.
     *
     * @param nombre nombre del tópico a inspeccionar
     * @return mapa con {@code nombre}, {@code particiones} y {@code replicacion}
     * @throws IllegalArgumentException si el tópico no existe
     * @throws RuntimeException         si la operación con el broker falla
     */
    public Map<String, Object> inspeccionarTopico(String nombre) {
        log.info("[KafkaAdmin] Inspeccionando tópico '{}'", nombre);
        try (AdminClient client = AdminClient.create(kafkaAdmin.getConfigurationProperties())) {
            Map<String, TopicDescription> descripcion =
                    client.describeTopics(List.of(nombre)).allTopicNames().get();

            if (!descripcion.containsKey(nombre)) {
                throw new IllegalArgumentException("El tópico '" + nombre + "' no existe en el broker");
            }

            TopicDescription td = descripcion.get(nombre);
            int particiones  = td.partitions().size();
            int replicacion  = td.partitions().isEmpty() ? 0
                    : td.partitions().get(0).replicas().size();

            log.debug("[KafkaAdmin] Tópico '{}': particiones={} réplicas={}", nombre, particiones, replicacion);

            return Map.of(
                    "nombre",      nombre,
                    "particiones", particiones,
                    "replicacion", replicacion,
                    "interno",     nombre.startsWith(PREFIJO_INTERNO)
            );
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Operación interrumpida al inspeccionar el tópico '" + nombre + "'", e);
        } catch (ExecutionException e) {
            throw new RuntimeException(
                    "Error al inspeccionar el tópico '" + nombre + "': " + e.getCause().getMessage(), e);
        }
    }

    /**
     * Crea un nuevo tópico dinámicamente en el broker.
     *
     * @param request datos del tópico a crear (nombre, particiones, replicación)
     * @throws RuntimeException si la creación falla en el broker
     */
    public void crearTopico(CrearTopicoRequest request) {
        log.info("[KafkaAdmin] Creando tópico '{}' con {} partición(es) y factor de replicación {}",
                request.nombre(), request.particiones(), request.replicacion());

        NewTopic nuevoTopico = new NewTopic(
                request.nombre(),
                request.particiones(),
                request.replicacion()
        );

        try (AdminClient client = AdminClient.create(kafkaAdmin.getConfigurationProperties())) {
            client.createTopics(List.of(nuevoTopico)).all().get();
            log.info("[KafkaAdmin] Tópico '{}' creado correctamente", request.nombre());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Operación interrumpida al crear el tópico '" + request.nombre() + "'", e);
        } catch (ExecutionException e) {
            throw new RuntimeException(
                    "Error al crear el tópico '" + request.nombre() + "': " + e.getCause().getMessage(), e);
        }
    }

    /**
     * Elimina un tópico del broker.
     *
     * <p><b>Restricción de seguridad:</b> los tópicos internos de Kafka
     * (prefijo {@code __}) no pueden eliminarse para evitar corrupción del broker.</p>
     *
     * @param nombre nombre del tópico a eliminar
     * @throws IllegalArgumentException si se intenta eliminar un tópico interno
     * @throws RuntimeException         si la eliminación falla en el broker
     */
    public void eliminarTopico(String nombre) {
        if (nombre.startsWith(PREFIJO_INTERNO)) {
            throw new IllegalArgumentException(
                    "No está permitido eliminar tópicos internos de Kafka: '" + nombre + "'");
        }

        log.info("[KafkaAdmin] Eliminando tópico '{}'", nombre);

        try (AdminClient client = AdminClient.create(kafkaAdmin.getConfigurationProperties())) {
            client.deleteTopics(List.of(nombre)).all().get();
            log.info("[KafkaAdmin] Tópico '{}' eliminado correctamente", nombre);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Operación interrumpida al eliminar el tópico '" + nombre + "'", e);
        } catch (ExecutionException e) {
            throw new RuntimeException(
                    "Error al eliminar el tópico '" + nombre + "': " + e.getCause().getMessage(), e);
        }
    }
}

