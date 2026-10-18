package ar.edu.utn.vastio.bebida.api;

import java.util.List;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import ar.edu.utn.vastio.bebida.aplicacion.ProveedorService.DatosProveedor;
import ar.edu.utn.vastio.bebida.aplicacion.ProveedorService.ProveedorConBebidas;
import ar.edu.utn.vastio.bebida.dominio.Bebida;

public final class ProveedorDto {

    private ProveedorDto() {
    }

    public record BebidaResumen(long id, String nombre, String presentacion) {
        static BebidaResumen de(Bebida b) {
            return new BebidaResumen(b.getId(), b.getNombre(), b.getPresentacion());
        }
    }

    /** {@code bebidas}: las que lo tienen como proveedor habitual. */
    public record ProveedorResponse(long id, String razonSocial, String cuit, String telefono, String email, boolean activo,
            List<BebidaResumen> bebidas) {
        static ProveedorResponse de(ProveedorConBebidas p) {
            var proveedor = p.proveedor();
            return new ProveedorResponse(proveedor.getId(), proveedor.getRazonSocial(), proveedor.getCuit(), proveedor.getTelefono(),
                    proveedor.getEmail(), proveedor.isActivo(), p.bebidas().stream().map(BebidaResumen::de).toList());
        }
    }

    /** CUIT con o sin guiones. */
    public record ProveedorRequest(
            @NotBlank(message = "Escribí la razón social.") @Size(max = 120, message = "Usá hasta 120 caracteres.") String razonSocial,
            @Size(max = 13, message = "El CUIT tiene 11 números.") String cuit,
            @Size(max = 30, message = "Usá hasta 30 caracteres.") String telefono,
            @Email(message = "Revisá el correo.") @Size(max = 120, message = "Usá hasta 120 caracteres.") String email) {

        DatosProveedor datos() {
            return new DatosProveedor(razonSocial.trim(), cuit, vacioANulo(telefono), vacioANulo(email));
        }

        private static String vacioANulo(String valor) {
            return valor == null || valor.isBlank() ? null : valor.trim();
        }
    }

    public record BebidasRequest(@NotNull(message = "Indicá las bebidas.") List<Long> bebidaIds) {
    }
}
