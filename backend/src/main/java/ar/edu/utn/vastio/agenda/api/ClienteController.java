package ar.edu.utn.vastio.agenda.api;

import java.util.List;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import ar.edu.utn.vastio.agenda.aplicacion.ClienteService;
import ar.edu.utn.vastio.agenda.dominio.Cliente;
import ar.edu.utn.vastio.comun.seguridad.Permisos;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1/clientes")
@Tag(name = "Clientes", description = "Búsqueda al registrar una pre-reserva o un evento")
public class ClienteController {

    private final ClienteService clientes;

    public ClienteController(ClienteService clientes) {
        this.clientes = clientes;
    }

    @GetMapping
    @PreAuthorize(Permisos.EDITAR_EVENTOS)
    @Operation(summary = "Buscar clientes", description = "Por documento exacto o parte del nombre; desde 2 caracteres, hasta 10 resultados.")
    public List<ClienteResponse> buscar(@RequestParam(defaultValue = "") String buscar) {
        return clientes.buscar(buscar).stream().map(ClienteResponse::de).toList();
    }

    public record ClienteResponse(long id, String nombre, String documento, String telefono, String email) {
        static ClienteResponse de(Cliente c) {
            return new ClienteResponse(c.getId(), c.getNombre(), c.getDocumento(), c.getTelefono(), c.getEmail());
        }
    }
}
