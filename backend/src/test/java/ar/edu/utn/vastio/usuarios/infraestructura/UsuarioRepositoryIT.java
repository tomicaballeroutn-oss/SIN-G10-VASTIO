package ar.edu.utn.vastio.usuarios.infraestructura;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

import ar.edu.utn.vastio.TestcontainersConfig;
import ar.edu.utn.vastio.usuarios.dominio.Rol;
import ar.edu.utn.vastio.usuarios.dominio.RolCodigo;
import ar.edu.utn.vastio.usuarios.dominio.Usuario;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(TestcontainersConfig.class)
class UsuarioRepositoryIT {

    private static final String HASH = "$2a$10$abcdefghijklmnopqrstuuOe1b0fK0f7Ztg8x9s0ZJdQy2o7p9aVwG";

    @Autowired
    UsuarioRepository usuarios;

    @Autowired
    RolRepository roles;

    @Autowired
    TestEntityManager em;

    @Test
    void losRolesDeV2EstanDisponiblesPorCodigo() {
        for (RolCodigo codigo : RolCodigo.values()) {
            assertThat(roles.findByCodigo(codigo)).as(codigo.name()).isPresent();
        }
    }

    @Test
    void guardaUnUsuarioConVariosPerfiles() {
        Usuario lucia = new Usuario("Lucía Ferreyra", "lucia.ferreyra", HASH);
        lucia.asignarRol(rol(RolCodigo.VENDEDORA));
        lucia.asignarRol(rol(RolCodigo.PLANNER));
        lucia.asignarRol(rol(RolCodigo.VENDEDORA));
        usuarios.save(lucia);
        em.flush();
        em.clear();

        Usuario leido = usuarios.findByNombreUsuario("lucia.ferreyra").orElseThrow();

        assertThat(leido.getId()).isNotNull();
        assertThat(leido.isActivo()).isTrue();
        assertThat(leido.isDebeCambiarContrasena()).isTrue();
        assertThat(leido.getFechaAlta()).isNotNull();
        assertThat(leido.codigosDeRol()).containsExactlyInAnyOrder(RolCodigo.VENDEDORA, RolCodigo.PLANNER);
    }

    @Test
    void laBajaEsLogicaYGuardaElMomento() {
        Usuario gabi = usuarios.save(new Usuario("Gabriela Paz", "gabi.paz", HASH));
        gabi.darDeBaja();
        em.flush();
        em.clear();

        Usuario leido = usuarios.findById(gabi.getId()).orElseThrow();

        assertThat(leido.isActivo()).isFalse();
        assertThat(leido.getFechaBaja()).isNotNull();
    }

    @Test
    void elNombreDeUsuarioEsUnico() {
        usuarios.saveAndFlush(new Usuario("Ana Sosa", "ana.sosa", HASH));

        assertThatThrownBy(() -> usuarios.saveAndFlush(new Usuario("Ana Sosa bis", "ana.sosa", HASH)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    private Rol rol(RolCodigo codigo) {
        return roles.findByCodigo(codigo).orElseThrow();
    }
}
