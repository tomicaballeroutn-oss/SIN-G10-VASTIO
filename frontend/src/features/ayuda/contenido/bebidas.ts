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
      notas: ['La carga de cantidades es manual. Esta operación traslada bebida del depósito madre al de transición y queda asociada al evento.', 'Si la barra se abastece directamente del depósito madre, la pantalla informa que el evento puede operar sin preparación. El traspaso conserva las cantidades entre ubicaciones y el consumo se registra en la operación de barra.'],
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
    descripcion: 'Al terminar, registrá qué botellas cerradas vuelven al depósito y cuáles quedan para el siguiente evento.',
    secciones: [{
      titulo: 'Registrar la devolución',
      pasos: ['Desde Barra, seleccioná Registrar devolución en la entrega correspondiente.', 'Por cada bebida, completá Vuelve al depósito y Queda en la barra (remanente).', 'Si la pantalla lo solicita, indicá también Queda en la transición para devolver ese sobrante al depósito madre.', 'Seleccioná Revisar devolución y luego Registrar devolución.'],
      notas: ['Las botellas abiertas se imputan como consumidas. La devolución y el remanente se cargan en botellas cerradas.', 'La entrega queda cerrada y el consumo se muestra después de registrar la devolución. Completá las devoluciones de todas las barras del evento.'],
    }],
  },
  cierre: {
    descripcion: 'El cierre fija el consumo real de bebidas del evento y lo lleva al estado Cerrado.',
    secciones: [{
      titulo: 'Cerrar el evento',
      pasos: ['Con el evento Realizado, abrí Barra y seleccioná Ir al cierre o Ir al cierre del evento.', 'Revisá los avisos de devoluciones pendientes y completalas en Barra.', 'Consultá lo Entregado, Devuelto, Remanente y el Consumo de todas las barras.', 'Seleccioná Cerrar evento y confirmá con Registrar cierre.'],
      notas: ['El evento debe estar Realizado y tener cerradas todas sus entregas.', 'Consumo real = Entregado − Devuelto − Remanente. Entregado incluye el remanente heredado de la jornada anterior.', 'El consumo queda fijado. Los ajustes posteriores corrigen el stock. La asistencia real por segmento puede completarse después por las personas autorizadas y actualizar el indicador por asistente.'],
    }],
  },
};
