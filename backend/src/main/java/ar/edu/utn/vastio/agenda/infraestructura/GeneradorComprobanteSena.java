package ar.edu.utn.vastio.agenda.infraestructura;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Currency;
import java.util.Locale;

import org.openpdf.text.Document;
import org.openpdf.text.DocumentException;
import org.openpdf.text.Element;
import org.openpdf.text.Font;
import org.openpdf.text.FontFactory;
import org.openpdf.text.Image;
import org.openpdf.text.PageSize;
import org.openpdf.text.Paragraph;
import org.openpdf.text.Phrase;
import org.openpdf.text.Rectangle;
import org.openpdf.text.pdf.PdfPCell;
import org.openpdf.text.pdf.PdfPTable;
import org.openpdf.text.pdf.PdfWriter;
import org.springframework.stereotype.Component;

import ar.edu.utn.vastio.VastioApplication;
import ar.edu.utn.vastio.agenda.dominio.Moneda;

/**
 * Arma el PDF del comprobante interno de la seña (M-07) con OpenPDF (LGPL/MPL, gratuita). Fuentes estándar del PDF:
 * cubren tildes, eñes y comillas latinas sin embeber archivos. Colores del sistema de diseño.
 */
@Component
public class GeneradorComprobanteSena {

    public static final String LEYENDA = "Comprobante interno, no válido como factura";

    private static final ZoneId ZONA = ZoneId.of(VastioApplication.ZONA_HORARIA);
    private static final Locale ES_AR = Locale.forLanguageTag("es-AR");
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final DateTimeFormatter FECHA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private static final Color MARCA = new Color(0x15, 0x3e, 0x6c);
    private static final Color TINTA = new Color(0x02, 0x12, 0x26);
    private static final Color APOYO = new Color(0x40, 0x4b, 0x5a);
    private static final Color FILO = new Color(0xa0, 0x9a, 0x93);

    private static final Font TITULO = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18, MARCA);
    private static final Font SALON = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16, TINTA);
    private static final Font ETIQUETA = FontFactory.getFont(FontFactory.HELVETICA, 9, APOYO);
    private static final Font VALOR = FontFactory.getFont(FontFactory.HELVETICA, 11, TINTA);
    private static final Font IMPORTE = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14, TINTA);
    private static final Font AVISO = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 10, MARCA);
    private static final Font PIE = FontFactory.getFont(FontFactory.HELVETICA, 8, APOYO);

    /** Lo que va impreso. {@code logo}: la imagen del salón (M-04) o null para poner su nombre. */
    public record Datos(String numero, OffsetDateTime emision, String salon, byte[] logo, String cliente,
            String clienteDocumento, String firmanteNombre, String firmanteDni, String tipoEvento, String evento,
            LocalDate fecha, String turno, BigDecimal importe, Moneda moneda, LocalDate fechaSena, String vendedora) {
    }

    public byte[] generar(Datos d) {
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        Document documento = new Document(PageSize.A4, 56, 56, 56, 56);
        try {
            PdfWriter.getInstance(documento, salida);
            documento.addTitle("Comprobante de seña " + d.numero());
            documento.addCreator("Vastio");
            documento.open();
            documento.add(encabezado(d));
            documento.add(aviso());
            documento.add(cuerpo(d));
            Paragraph pie = new Paragraph("Emitido con Vastio el " + d.emision().atZoneSameInstant(ZONA).format(FECHA_HORA)
                    + ". " + LEYENDA + ".", PIE);
            pie.setSpacingBefore(24);
            documento.add(pie);
        } catch (DocumentException | IOException e) {
            throw new IllegalStateException("No se pudo armar el comprobante " + d.numero(), e);
        } finally {
            if (documento.isOpen()) {
                documento.close();
            }
        }
        return salida.toByteArray();
    }

    /** A la izquierda, el logo del salón o su nombre; a la derecha, título, número y fecha de emisión. */
    private PdfPTable encabezado(Datos d) throws IOException {
        PdfPTable tabla = new PdfPTable(new float[] {1, 1});
        tabla.setWidthPercentage(100);
        PdfPCell salon;
        if (d.logo() != null) {
            Image logo = Image.getInstance(d.logo());
            logo.scaleToFit(160, 64);
            salon = new PdfPCell(logo, false);
        } else {
            salon = new PdfPCell(new Phrase(d.salon(), SALON));
        }
        salon.setBorder(Rectangle.NO_BORDER);
        salon.setVerticalAlignment(Element.ALIGN_MIDDLE);
        tabla.addCell(salon);

        Paragraph titulo = new Paragraph();
        titulo.add(new Paragraph("Comprobante de seña", TITULO));
        titulo.add(new Paragraph("N.º " + d.numero(), VALOR));
        titulo.add(new Paragraph("Emitido el " + d.emision().atZoneSameInstant(ZONA).format(FECHA), ETIQUETA));
        PdfPCell derecha = new PdfPCell();
        derecha.addElement(titulo);
        derecha.setBorder(Rectangle.NO_BORDER);
        derecha.setHorizontalAlignment(Element.ALIGN_RIGHT);
        titulo.setAlignment(Element.ALIGN_RIGHT);
        tabla.addCell(derecha);
        tabla.setSpacingAfter(16);
        return tabla;
    }

    private Paragraph aviso() {
        Paragraph aviso = new Paragraph(LEYENDA, AVISO);
        aviso.setSpacingAfter(16);
        return aviso;
    }

    private PdfPTable cuerpo(Datos d) {
        PdfPTable tabla = new PdfPTable(new float[] {1, 2});
        tabla.setWidthPercentage(100);
        fila(tabla, "Importe de la seña", importe(d.importe(), d.moneda()), IMPORTE);
        fila(tabla, "Fecha de la seña", d.fechaSena().format(FECHA), VALOR);
        fila(tabla, "Cliente", d.clienteDocumento() == null ? d.cliente() : d.cliente() + " · DNI " + d.clienteDocumento(), VALOR);
        fila(tabla, "Firmante", firmante(d), VALOR);
        fila(tabla, "Evento", d.tipoEvento() + " · " + d.evento(), VALOR);
        fila(tabla, "Salón", d.salon(), VALOR);
        fila(tabla, "Fecha y turno", d.fecha().format(FECHA) + " · " + d.turno(), VALOR);
        fila(tabla, "Vendedora", d.vendedora(), VALOR);
        return tabla;
    }

    private static String firmante(Datos d) {
        String dni = d.firmanteDni() == null ? "" : "DNI " + d.firmanteDni();
        if (d.firmanteNombre() == null) {
            return dni;
        }
        return dni.isEmpty() ? d.firmanteNombre() : d.firmanteNombre() + " · " + dni;
    }

    private static void fila(PdfPTable tabla, String etiqueta, String valor, Font fuente) {
        PdfPCell izquierda = new PdfPCell(new Phrase(etiqueta, ETIQUETA));
        PdfPCell derecha = new PdfPCell(new Phrase(valor, fuente));
        for (PdfPCell celda : new PdfPCell[] {izquierda, derecha}) {
            celda.setBorder(Rectangle.BOTTOM);
            celda.setBorderColor(FILO);
            celda.setPaddingTop(8);
            celda.setPaddingBottom(8);
            celda.setVerticalAlignment(Element.ALIGN_MIDDLE);
            tabla.addCell(celda);
        }
    }

    /** 1500, USD → «US$ 1.500,00»; 150000, ARS → «$ 150.000,00». Sin conversión. */
    static String importe(BigDecimal importe, Moneda moneda) {
        NumberFormat formato = NumberFormat.getCurrencyInstance(ES_AR);
        formato.setCurrency(Currency.getInstance(moneda.name()));
        return formato.format(importe).replace(' ', ' ');
    }
}
