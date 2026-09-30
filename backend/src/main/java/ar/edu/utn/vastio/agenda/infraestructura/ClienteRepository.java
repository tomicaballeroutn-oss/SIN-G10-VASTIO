package ar.edu.utn.vastio.agenda.infraestructura;

import java.util.List;

import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import ar.edu.utn.vastio.agenda.dominio.Cliente;

public interface ClienteRepository extends JpaRepository<Cliente, Long> {

    /**
     * Por documento exacto, o por nombre que contenga el texto (sin distinguir mayúsculas).
     * Primero los que empiezan con el texto.
     */
    @Query("""
            select c from Cliente c
            where c.documento = :texto or lower(c.nombre) like concat('%', lower(:texto), '%')
            order by case when lower(c.nombre) like concat(lower(:texto), '%') then 0 else 1 end, c.nombre
            """)
    List<Cliente> buscar(String texto, Limit limite);
}
