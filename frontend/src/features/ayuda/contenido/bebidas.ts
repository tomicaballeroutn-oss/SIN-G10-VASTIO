import { ROLES_GESTIONAR_ORDEN } from '../articulos';
import type { ContenidoAyuda } from '../modelo';

export const BEBIDAS: Record<string, ContenidoAyuda> = {
  existencias: {
    descripcion: 'Consultá las cantidades de bebidas disponibles en las ubicaciones habilitadas para tu perfil.',
    secciones: [{
      titulo: 'Consultar saldos',
      pasos: ['Ingresá a Existencias.', 'Seleccioná una Ubicación. Los perfiles con acceso general también pueden consultar Todas las ubicaciones (total).', 'Usá Buscar bebida y consultá el saldo expresado en cajas y botellas.', 'Seleccioná Actualizar para refrescar los saldos.'],
      notas: ['Barra consulta las barras habilitadas de la jornada. Dirección, Coordinación comercial, Administración y Compras pueden consultar todas las ubicaciones.', 'Sin stock corresponde a saldo cero o menor. Bajo se muestra en el depósito madre cuando se alcanza el mínimo configurado. Un saldo negativo muestra Falta registrar un movimiento.'],
    }],
  },
  'ingresos-ajustes': {
    descripcion: 'Registrá la mercadería que llega al depósito madre y los ajustes necesarios después de un recuento físico o una rotura.',
    secciones: [{
      titulo: 'Registrar un ingreso',
      pasos: ['Abrí Ingreso de mercadería.', 'Revisá Fecha de ingreso y completá Número de remito si corresponde. El destino es el depósito madre.', 'Agregá las bebidas por búsqueda o con Leer código. Indicá la cantidad en cajas y botellas.', 'Seleccioná Revisar ingreso y luego Registrar ingreso.'],
      notas: ['La fecha puede ser hoy o anterior. El código identifica la bebida y la cantidad se carga a mano. La cámara requiere HTTPS y permiso del navegador.', 'Un ingreso registrado se corrige mediante un ajuste de stock.'],
    }, {
      titulo: 'Registrar un ajuste',
      pasos: ['Abrí Existencias y elegí una ubicación concreta.', 'En la bebida, seleccioná Registrar recuento para cargar la cantidad contada o Declarar rotura para registrar la merma.', 'Completá las cantidades y el motivo que solicita el formulario.', 'Revisá y confirmá la operación con la acción del formulario.'],
      notas: ['El ajuste agrega un movimiento y conserva los registros anteriores. Dirección, Coordinación comercial, Administración y Compras tienen acceso a estas operaciones.'],
    }],
  },
  'ordenes-preparacion': {
    descripcion: 'Consultá la bebida sugerida para el evento y registrá los traspasos de preparación cuando el circuito utiliza un depósito de transición.',
    secciones: [{
      titulo: 'Consultar una orden',
      pasos: ['Ingresá a Órdenes de preparación y elegí el evento, o abrí la pestaña Orden de preparación de su ficha.', 'Revisá Cantidad propuesta, A retirar y Comprometido por otros eventos, junto con el origen de la sugerencia.', 'Usá Vista para imprimir si necesitás una copia de la orden.'],
      notas: ['La orden se genera al confirmar el evento. Puede estar Sugerida, Modificada, Confirmada, Cerrada o Descartada.', 'Administración consulta las órdenes. Dirección, Coordinación comercial y Compras también pueden gestionarlas.'],
    }, {
      titulo: 'Gestionar una orden',
      roles: ROLES_GESTIONAR_ORDEN,
      pasos: ['Si el evento habilitado está Sin orden de preparación, seleccioná Generar orden.', 'Usá Ajustar cantidades y guardá los cambios. Recalcular sugerencia vuelve a calcular con los asistentes actuales y reemplaza los ajustes.', 'Seleccioná Confirmar orden y revisá la confirmación.', 'Si necesitás cambiar una orden confirmada, seleccioná Reabrir orden, ajustá las cantidades y volvé a confirmarla.'],
      notas: ['Las acciones visibles dependen del estado. La orden se cierra con el cierre del evento y se descarta al cancelarlo.', 'La sugerencia informa si proviene del histórico o de proporciones configuradas. Si faltan datos, la orden puede salir vacía para cargar las bebidas a mano.'],
    }, {
      titulo: 'Registrar una preparación',
      pasos: ['Abrí un evento Confirmado o En curso e ingresá a Preparación.', 'Elegí la Barra y verificá Sale de y Destino.', 'Agregá las bebidas y cantidades en cajas y botellas enteras.', 'Seleccioná Revisar preparación y luego Registrar preparación.'],
      notas: ['La carga de cantidades es manual. Esta operación traslada bebida del depósito madre al de transición y queda asociada al evento.', 'Si la barra se abastece directamente del depósito madre, la pantalla informa que el evento puede operar sin preparación. El traspaso conserva las cantidades entre ubicaciones. El consumo se calcula y registra al confirmar la devolución de cada barra.'],
    }],
  },
  retiros: {
    descripcion: 'Durante un evento En curso, registrá las bebidas que se entregan a la barra.',
    secciones: [{
      titulo: 'Registrar un retiro',
      pasos: ['Ingresá a Barra y seleccioná Evento y barra.', 'Buscá las bebidas en Agregar bebida o utilizá Escanear caja.', 'Indicá las cantidades en cajas y botellas. Revisá el origen del retiro.', 'Seleccioná Revisar retiro y confirmá con Entregar a barra o Registrar retiro adicional, según corresponda.'],
      notas: ['El primer retiro abre la entrega. Los siguientes quedan asociados al mismo evento y barra como retiros adicionales.', 'Barra opera sobre las jornadas habilitadas y carga cantidades sin consultar el saldo del depósito. Un retiro puede dejar saldo teórico negativo y generar un aviso a las áreas responsables.'],
    }],
  },
  devoluciones: {
    descripcion: 'Al finalizar la operación de barra, registrá las bebidas que vuelven al depósito y las que quedan como remanente. Al confirmar la devolución, Vastio calcula y registra el consumo real de cada bebida.',
    secciones: [{
      titulo: 'Registrar la devolución',
      pasos: ['Desde Barra, seleccioná Registrar devolución en la entrega correspondiente.', 'Por cada bebida, indicá cuánto Vuelve al depósito y cuánto Queda en la barra (remanente).', 'Si corresponde, registrá también las bebidas que quedan en el depósito de transición.', 'Seleccioná Revisar devolución y luego Registrar devolución.', 'Consultá el consumo real calculado por el sistema.'],
      notas: [
        'El consumo se calcula mediante la fórmula: Consumo = Entregado − Devuelto − Remanente.',
        'Al confirmar la devolución, se registran los movimientos de consumo y se actualizan las existencias.',
        'Las botellas abiertas se consideran consumidas. Solamente las botellas cerradas pueden devolverse o quedar como remanente.',
        'La entrega queda cerrada y no admite nuevos retiros.',
        'El cierre posterior del evento no vuelve a descontar esas bebidas del stock.',
      ],
    }],
  },
  cierre: {
    descripcion: 'El cierre del evento verifica las devoluciones y consolida definitivamente el consumo registrado por las barras, sin volver a descontar bebidas del stock.',
    secciones: [{
      titulo: 'Cerrar el evento',
      pasos: ['Con el evento en estado Realizado, ingresá a Barra y abrí el cierre correspondiente.', 'Revisá si existen devoluciones pendientes y completalas antes de continuar.', 'Consultá las cantidades entregadas, devueltas, remanentes y consumidas de todas las barras.', 'Seleccioná Cerrar evento.', 'Confirmá la operación mediante Registrar cierre.'],
      notas: [
        'El evento debe estar en estado Realizado y todas sus entregas deben estar cerradas.',
        'El consumo de cada barra ya fue calculado y registrado al confirmar su devolución.',
        'Durante el cierre, Vastio consolida esos consumos y guarda el resultado definitivo en consumo_evento.',
        'El cierre no genera nuevamente movimientos CONSUMO ni descuenta otra vez las bebidas del stock.',
        'El evento pasa a estado Cerrado y la orden de preparación se cierra si existe.',
        'Los ajustes de stock posteriores no modifican el consumo consolidado del evento.',
        'La asistencia real por segmento puede actualizar posteriormente el indicador de consumo por asistente, sin alterar las cantidades consumidas.',
        'Entregado incluye el remanente heredado de la jornada anterior.',
      ],
    }],
  },
};
