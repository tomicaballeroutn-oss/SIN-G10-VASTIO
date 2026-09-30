package ar.edu.utn.vastio.agenda.aplicacion;

import java.util.List;

import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import ar.edu.utn.vastio.agenda.dominio.Cliente;
import ar.edu.utn.vastio.agenda.infraestructura.ClienteRepository;
import ar.edu.utn.vastio.comun.errores.ProblemaException;

/**
 * Clientes: se buscan por nombre o documento y, si no existen, se crean al pre-reservar.
 */
@Service
@Transactional(readOnly = true)
public class ClienteService {

    private static final int RESULTADOS = 10;

    private final ClienteRepository clientes;

    public ClienteService(ClienteRepository clientes) {
        this.clientes = clientes;
    }

    /**
     * Un cliente existente ({@code id}) o uno nuevo ({@code nombre} y el resto opcional). Si es existente,
     * teléfono y correo, cuando vienen, actualizan los que tenía.
     */
    public record DatosCliente(Long id, String nombre, String documento, String telefono, String email) {
    }

    public List<Cliente> buscar(String texto) {
        String limpio = texto == null ? "" : texto.trim();
        if (limpio.length() < 2) {
            return List.of();
        }
        return clientes.buscar(limpio, Limit.of(RESULTADOS));
    }

    public Cliente cliente(long id) {
        return clientes.findById(id).orElseThrow(() -> ProblemaException.noEncontrado("CLIENTE_INEXISTENTE",
                "No encontramos ese cliente."));
    }

    @Transactional
    public Cliente obtenerOCrear(DatosCliente datos) {
        if (datos.id() != null) {
            Cliente cliente = cliente(datos.id());
            cliente.actualizar(cliente.getNombre(), cliente.getDocumento(),
                    siHay(datos.telefono(), cliente.getTelefono()), siHay(datos.email(), cliente.getEmail()));
            return cliente;
        }
        return clientes.save(new Cliente(datos.nombre().trim(), vacioANulo(datos.documento()),
                vacioANulo(datos.telefono()), vacioANulo(datos.email())));
    }

    private static String siHay(String nuevo, String actual) {
        return nuevo == null || nuevo.isBlank() ? actual : nuevo.trim();
    }

    private static String vacioANulo(String valor) {
        return valor == null || valor.isBlank() ? null : valor.trim();
    }
}
