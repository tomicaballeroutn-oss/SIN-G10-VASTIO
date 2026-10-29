package ar.edu.utn.vastio.agenda.infraestructura;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import com.google.api.client.http.*;

/** Impone límites al transporte OAuth aunque la biblioteca de autenticación use sus propios defaults. */
final class TransporteTokenCalendar extends HttpTransport {
    private final HttpTransport delegado;
    private final int conexionMs, lecturaMs;
    TransporteTokenCalendar(HttpTransport delegado,int conexionMs,int lecturaMs) {
        this.delegado=delegado;this.conexionMs=conexionMs;this.lecturaMs=lecturaMs;
    }
    @Override protected LowLevelHttpRequest buildRequest(String metodo,String url) {
        return new LowLevelHttpRequest() {
            final List<String[]> cabeceras=new ArrayList<>();
            @Override public void addHeader(String nombre,String valor) { cabeceras.add(new String[]{nombre,valor}); }
            @Override public LowLevelHttpResponse execute() throws IOException {
                HttpContent contenido=getStreamingContent()==null?null:new AbstractHttpContent(getContentType()) {
                    @Override public void writeTo(OutputStream salida) throws IOException { getStreamingContent().writeTo(salida); }
                    @Override public long getLength() { return getContentLength(); }
                    @Override public boolean retrySupported() { return false; }
                };
                var pedido=delegado.createRequestFactory().buildRequest(metodo,new GenericUrl(url),contenido);
                pedido.setConnectTimeout(conexionMs);pedido.setReadTimeout(lecturaMs);pedido.setNumberOfRetries(0);
                pedido.setFollowRedirects(false);pedido.setThrowExceptionOnExecuteError(false);
                pedido.setLoggingEnabled(false);pedido.setCurlLoggingEnabled(false);
                for(var c:cabeceras) pedido.getHeaders().set(c[0],c[1]);
                var respuesta=pedido.execute();
                var nombres=new ArrayList<String>();var valores=new ArrayList<String>();
                respuesta.getHeaders().forEach((nombre,valor)->{
                    if(valor instanceof List<?> lista) for(var v:lista) { nombres.add(nombre);valores.add(String.valueOf(v)); }
                    else if(valor!=null) { nombres.add(nombre);valores.add(String.valueOf(valor)); }
                });
                return new LowLevelHttpResponse() {
                    @Override public InputStream getContent() throws IOException { return respuesta.getContent(); }
                    @Override public String getContentEncoding() { return respuesta.getContentEncoding(); }
                    @Override public long getContentLength() { return respuesta.getHeaders().getContentLength()==null?-1:respuesta.getHeaders().getContentLength(); }
                    @Override public String getContentType() { return respuesta.getContentType(); }
                    @Override public String getStatusLine() { return respuesta.getStatusCode()+" "+respuesta.getStatusMessage(); }
                    @Override public int getStatusCode() { return respuesta.getStatusCode(); }
                    @Override public String getReasonPhrase() { return respuesta.getStatusMessage(); }
                    @Override public int getHeaderCount() { return nombres.size(); }
                    @Override public String getHeaderName(int i) { return nombres.get(i); }
                    @Override public String getHeaderValue(int i) { return valores.get(i); }
                    @Override public void disconnect() throws IOException { respuesta.disconnect(); }
                };
            }
        };
    }
}
