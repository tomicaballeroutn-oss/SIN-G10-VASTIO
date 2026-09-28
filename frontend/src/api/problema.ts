/**
 * Error de la API. El backend responde ProblemDetail (RFC 9457) con `detail` listo para mostrar
 * en voseo y `codigo` estable para reaccionar (SESION_VENCIDA, CREDENCIALES_INVALIDAS, …).
 */
export interface ErrorDeCampo {
  campo: string;
  mensaje: string;
}

export class ProblemaApi extends Error {
  readonly status: number;
  readonly codigo: string;
  readonly titulo: string;
  readonly errores: ErrorDeCampo[];

  constructor(status: number, codigo: string, titulo: string, detalle: string, errores: ErrorDeCampo[] = []) {
    super(detalle);
    this.name = 'ProblemaApi';
    this.status = status;
    this.codigo = codigo;
    this.titulo = titulo;
    this.errores = errores;
  }

  /** Mensaje de un campo puntual, para pasarlo como `error` a Input/Select. */
  errorDe(campo: string): string | undefined {
    return this.errores.find((e) => e.campo === campo)?.mensaje;
  }

  static async desde(respuesta: Response): Promise<ProblemaApi> {
    let cuerpo: Record<string, unknown> = {};
    try {
      cuerpo = await respuesta.json();
    } catch {
      // sin cuerpo o no es JSON: queda el mensaje genérico
    }
    return new ProblemaApi(
      respuesta.status,
      typeof cuerpo.codigo === 'string' ? cuerpo.codigo : `HTTP_${respuesta.status}`,
      typeof cuerpo.title === 'string' ? cuerpo.title : 'Error',
      typeof cuerpo.detail === 'string' ? cuerpo.detail : MENSAJE_SIN_CONEXION,
      Array.isArray(cuerpo.errores) ? (cuerpo.errores as ErrorDeCampo[]) : [],
    );
  }

  static sinConexion(): ProblemaApi {
    return new ProblemaApi(0, 'SIN_CONEXION', 'Sin conexión', MENSAJE_SIN_CONEXION);
  }
}

export const MENSAJE_SIN_CONEXION = 'No pudimos comunicarnos con el sistema. Revisá la conexión y volvé a intentarlo.';
