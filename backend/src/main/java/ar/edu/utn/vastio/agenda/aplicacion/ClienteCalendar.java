package ar.edu.utn.vastio.agenda.aplicacion;

import ar.edu.utn.vastio.agenda.aplicacion.ContenidoCalendar.Contenido;

public interface ClienteCalendar {
    record Remoto(String etag, String huella, boolean eliminado) { }
    boolean configurado();
    void comprobarDestino(String calendario);
    Remoto consultar(String calendario, String id);
    Remoto crear(String calendario, String id, Contenido contenido);
    Remoto actualizar(String calendario, String id, Contenido contenido, String etag);
    void retirar(String calendario, String id, String etag);

    /** Código cerrado y sanitizado. No propaga cuerpos HTTP, tokens ni URLs de Google. */
    class Fallo extends RuntimeException {
        private final String codigo;
        private final boolean incierto;
        public Fallo(String codigo, boolean incierto) {
            super(codigo); this.codigo = codigo; this.incierto = incierto;
        }
        public String codigo() { return codigo; }
        public boolean incierto() { return incierto; }
    }
}
