/**
 * Base común: errores, seguridad, auditoría y utilidades compartidas por los módulos.
 *
 * <p>Capas: {@code api} (controllers y DTO) · {@code aplicacion} (servicios y casos de uso) ·
 * {@code dominio} (entidades y reglas) · {@code infraestructura} (repositorios).
 * Un módulo no accede a los repositorios de otro: usa su servicio.
 */
package ar.edu.utn.vastio.comun;
