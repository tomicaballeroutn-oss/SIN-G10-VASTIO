import type { ContenidoAyuda } from '../modelo';

export const GESTION: Record<string, ContenidoAyuda> = {
  ocupacion: {
    descripcion: 'Consultá el uso comercial de los salones durante un período. Este reporte está disponible para Dirección y Coordinación comercial.',
    secciones: [{
      titulo: 'Consultar el reporte',
      pasos: ['Ingresá a Reportes.', 'Seleccioná el período con Desde y Hasta. En el celular, abrí Filtrar para elegir el período y los filtros.', 'Elegí los filtros de Salón, Tipo de evento, Vendedora o Temporada que necesitás.', 'En el celular, seleccioná Ver resultados. En escritorio, los resultados se actualizan al cambiar los filtros. Podés usar Imprimir sobre el resultado vigente.'],
      notas: ['Se incluyen eventos Señados, Contratados, Confirmados, En curso, Realizados y Cerrados. Las pre-reservas, cancelaciones y liberaciones quedan fuera del reporte.', 'Las unidades bloqueadas se descuentan de la capacidad disponible del período.'],
    }],
  },
  'estadisticas-consumo': {
    descripcion: 'Analizá el consumo histórico de bebidas en eventos cerrados con indicadores generales y desgloses.',
    secciones: [{
      titulo: 'Consultar las estadísticas',
      pasos: ['Ingresá a Estadísticas de consumo.', 'Seleccioná Fecha desde, Fecha hasta, Tipo de evento, Salón y Bebida según la consulta que necesites. Sin fechas se consulta todo el historial.', 'Seleccioná Consultar estadísticas.', 'Revisá el consumo total, el promedio por evento y las botellas por asistente. En el celular podés desplazar las tablas hacia los costados.'],
      notas: ['El período corresponde a la fecha de realización y se consideran únicamente eventos cerrados.', 'El indicador por asistente usa la asistencia real por segmentos cuando está registrada. En los demás casos usa la cantidad de invitados. Los eventos sin asistentes válidos quedan fuera de ese cálculo.', 'Los eventos sin consumo registrado cuentan como cero en el promedio por evento. La pantalla muestra cantidades de bebida.'],
    }],
  },
  'usuarios-perfiles': {
    descripcion: 'Dirección y Coordinación comercial administran las cuentas y los perfiles de las personas que utilizan Vastio.',
    secciones: [{
      titulo: 'Registrar un usuario',
      pasos: ['Ingresá a Usuarios y perfiles.', 'Seleccioná Nuevo usuario.', 'Completá Nombre y apellido, Usuario, Contraseña inicial y los perfiles que correspondan. Correo y Teléfono son opcionales.', 'Seleccioná Dar de alta usuario. En su primer ingreso, la persona deberá elegir una contraseña propia.'],
      notas: ['Modificar permite actualizar los datos y los perfiles. El nombre de usuario se conserva.', 'Dar de baja y Reactivar administran la disponibilidad de una cuenta. Revisá los avisos sobre eventos activos al dar de baja.', 'Una persona puede tener varios perfiles y reúne los permisos de todos ellos.'],
    }],
  },
  parametros: {
    descripcion: 'Parámetros reúne las configuraciones utilizadas en las demás pantallas. Acceden Dirección, Coordinación comercial y Administración.',
    secciones: [{
      titulo: 'Consultar y modificar la configuración',
      pasos: ['Ingresá a Parámetros.', 'Elegí una pestaña. En el celular, utilizá el selector Sección.', 'Buscá el elemento que querés consultar o modificar y abrí la acción disponible.', 'Completá los campos, revisá los mensajes del formulario y guardá los cambios.'],
      notas: ['Las secciones son Salones, Turnos, Tipos de evento, Segmentos de asistencia, Categorías de servicio, Motivos, Ubicaciones de stock, Proporciones de consumo y Generales.', 'Los cambios pueden afectar los datos utilizados por otras pantallas. Revisá las restricciones que informa Vastio para cada configuración.'],
    }],
  },
  cocina: {
    descripcion: 'Consultá la información gastronómica de los próximos eventos en una vista de lectura.',
    secciones: [{
      titulo: 'Consultar los eventos de un día',
      pasos: ['Ingresá a Vista de cocina.', 'Seleccioná un día del calendario.', 'Consultá las tarjetas de los eventos y revisá invitados y servicios gastronómicos.', 'Si aparece Jornadas anteriores, revisá allí los eventos En curso que cruzaron la medianoche.'],
      notas: ['La vista incluye eventos Confirmados o En curso dentro del horizonte configurado. Los eventos En curso de jornadas anteriores siguen visibles.', 'Dirección, Coordinación comercial, Planner, Compras y Cocina pueden consultar esta pantalla. La información se limita a lo necesario para la preparación gastronómica.'],
    }],
  },
};
