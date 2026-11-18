import { ROLES_CONFIRMAR, ROLES_PRE_RESERVA } from '../articulos';
import type { ContenidoAyuda } from '../modelo';

export const AGENDA: Record<string, ContenidoAyuda> = {
  'utilizar-agenda': {
    descripcion: 'Consultá la disponibilidad y ocupación de los salones. Cada día tiene seis unidades, una por salón y turno de Mediodía o Noche.',
    secciones: [{
      titulo: 'Consultar un día',
      pasos: ['Ingresá a Agenda.', 'Navegá hasta el mes que querés consultar con las flechas o el selector de mes y año. Ir a hoy vuelve a la jornada actual.', 'Seleccioná un día para ver sus turnos y elegí el salón en el detalle.', 'Abrí una unidad ocupada para consultar la ficha, cuando tu perfil tenga acceso a ese evento.'],
      notas: ['Una unidad Disponible permite iniciar una pre-reserva a las personas autorizadas. Las unidades bloqueadas se identifican con un candado.', 'La vendedora puede abrir únicamente las fichas de sus propios eventos.'],
    }],
  },
  'filtros-estados': {
    descripcion: 'Destacá las unidades que coinciden con el salón, turno y estado que necesitás consultar.',
    secciones: [{
      titulo: 'Utilizar los filtros',
      pasos: ['Ingresá a Agenda.', 'Seleccioná los salones, Turnos y estados que querés consultar. En el celular, el filtro Estado está junto al calendario.', 'Revisá las unidades destacadas. Las demás se atenúan y conservan visible su ocupación.', 'Seleccioná Limpiar filtros para restablecer la visualización.'],
    }, {
      titulo: 'Estados del evento',
      notas: [
        'Pre-reserva aparta la fecha mientras se negocia y permanece hasta que se señe o se libere.',
        'Señado indica que se registró la seña. Contratado indica que se registró la firma del contrato.',
        'Confirmado indica que se completaron los requisitos operativos. En curso corresponde al desarrollo del evento y Realizado al turno terminado, pendiente del cierre que consolida el consumo de las barras.',
        'Cerrado indica que se consolidó definitivamente el consumo de bebida. La asistencia real por segmento puede completarse después por las personas autorizadas.',
        'Liberada y Cancelado liberan la unidad. Se consultan en Eventos o Mis eventos. El calendario ofrece los estados activos, Disponible y Bloqueado.',
        'Un bloqueo retira unidades de la venta por motivos no comerciales. Reprogramar conserva el estado del evento y queda registrado en su historial.',
      ],
    }],
  },
  'ficha-historial': {
    descripcion: 'La ficha reúne los datos del evento y las operaciones realizadas sobre él. Las pestañas y acciones disponibles dependen de tu perfil y del estado.',
    secciones: [{
      titulo: 'Consultar el historial',
      pasos: ['Abrí un evento desde Agenda o Eventos. Para la vendedora, la sección se llama Mis eventos.', 'Ingresá a la pestaña Historial.', 'Revisá los cambios registrados, sus responsables y fechas.'],
      notas: ['Los cambios automáticos aparecen atribuidos a Sistema. El historial conserva los registros de las operaciones.', 'Datos, Servicios, Asistencia, Documentos y las pestañas de bebidas se ofrecen según los permisos y las características del evento.'],
    }],
  },
  'pre-reservas-senas': {
    descripcion: 'Una pre-reserva aparta una unidad mientras se negocia con el cliente. La seña convierte esa pre-reserva en una reserva firme.',
    secciones: [{
      titulo: 'Registrar una pre-reserva',
      pasos: ['En Agenda, seleccioná el día y una unidad Disponible.', 'Elegí o registrá el cliente y completá los datos solicitados del evento.', 'Revisá el salón, la fecha y el turno, y seleccioná Registrar pre-reserva.'],
      notas: ['La pre-reserva permanece hasta que se señe o se libere. Si la unidad fue ocupada por otra persona, elegí otra disponible.', 'Dirección, Coordinación comercial y Vendedora registran pre-reservas. La vendedora opera sobre sus propios eventos.'],
    }, {
      titulo: 'Registrar la seña',
      pasos: ['Abrí un evento en estado Pre-reserva.', 'Seleccioná Registrar seña.', 'Indicá Moneda, Importe de la seña, Fecha del pago y DNI del firmante. Completá los demás datos cuando corresponda.', 'Seleccioná Confirmar seña. El evento pasa a Señado.'],
      notas: ['Si la negociación termina, usá Liberar pre-reserva desde la ficha y revisá la confirmación.'],
    }],
  },
  'confirmacion-reprogramacion': {
    descripcion: 'Completá la confirmación operativa o cambiá la fecha, el turno o el salón con las acciones habilitadas en la ficha del evento.',
    secciones: [{
      titulo: 'Confirmar un evento',
      roles: ROLES_CONFIRMAR,
      pasos: ['Abrí la ficha del evento Contratado.', 'Seleccioná Confirmar evento y revisá los requisitos que muestra Vastio.', 'Completá los pendientes, como la planner asignada, los invitados definitivos y los servicios requeridos.', 'Volvé a Confirmar evento y confirmá cuando todos los requisitos estén cumplidos.'],
      notas: ['Dirección, Coordinación comercial y la planner asignada pueden confirmar. La aplicación informa los requisitos pendientes y avisa a las áreas correspondientes al confirmar.'],
    }, {
      titulo: 'Reprogramar un evento',
      roles: ROLES_PRE_RESERVA,
      pasos: ['Abrí la ficha y seleccioná Reprogramar.', 'Elegí una unidad disponible en el calendario y revisá el salón, la fecha y el turno de destino.', 'Completá Motivo de la reprogramación cuando se solicite y, si corresponde, Detalle.', 'Seleccioná Confirmar reprogramación para guardar el cambio.'],
      notas: ['Dirección, Coordinación comercial y la vendedora titular pueden reprogramar los eventos habilitados. En Pre-reserva, el cambio de fecha queda como una modificación sin motivo.', 'Las unidades ocupadas o bloqueadas quedan fuera de la selección. El evento conserva su estado y el historial de los cambios.'],
    }],
  },
};
