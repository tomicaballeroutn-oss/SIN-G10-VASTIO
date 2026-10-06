package ar.edu.utn.vastio.agenda.api;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.util.List;

import jakarta.validation.constraints.NotNull;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.multipart.MultipartFile;

import ar.edu.utn.vastio.agenda.aplicacion.ContratoService.Archivo;

/**
 * Registrar firma de contrato (UI-12), como {@code multipart/form-data}: {@code fechaFirma} (aaaa-mm-dd) y uno o más
 * {@code archivos}. Formato, tamaño y cantidad de archivos los valida el servicio.
 */
public record ContratoRequest(
        @NotNull(message = "Elegí la fecha de firma.") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fechaFirma,
        List<MultipartFile> archivos) {

    List<Archivo> archivosLeidos() {
        if (archivos == null) {
            return List.of();
        }
        return archivos.stream().map(a -> {
            try {
                return new Archivo(a.getOriginalFilename(), a.getBytes());
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }).toList();
    }
}
