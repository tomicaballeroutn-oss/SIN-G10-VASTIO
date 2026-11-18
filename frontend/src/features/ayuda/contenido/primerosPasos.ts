import type { ContenidoAyuda } from '../modelo';

export const PRIMEROS_PASOS: Record<string, ContenidoAyuda> = {
  ingresar: {
    descripcion: 'Accedé a las funciones del complejo con tu cuenta personal. Las pantallas y acciones disponibles dependen de tus perfiles.',
    secciones: [{
      titulo: 'Cómo ingresar',
      pasos: ['Abrí Vastio desde tu navegador.', 'Ingresá tu usuario y contraseña.', 'Seleccioná Ingresar.'],
      notas: ['En el primer ingreso, elegí una contraseña propia en Mi cuenta antes de continuar.', 'Si los datos son incorrectos, revisá el mensaje y volvé a intentarlo. Si tu sesión está por vencer, el aviso permite Seguir trabajando.'],
    }],
  },
  'mi-cuenta': {
    descripcion: 'Desde Mi cuenta podés cambiar tu contraseña y elegir el aspecto visual de Vastio.',
    secciones: [{
      titulo: 'Cambiar tu contraseña',
      pasos: ['Ingresá a Mi cuenta desde las opciones de tu usuario. En el celular, abrí Más.', 'Escribí tu Contraseña actual.', 'Ingresá la Contraseña nueva y completá Repetí la contraseña nueva.', 'Seleccioná Actualizar contraseña.'],
      notas: ['Elegí una contraseña distinta de la actual, de 8 a 72 caracteres. Los acentos y otros caracteres especiales pueden requerir una contraseña más corta.', 'En Tema de la pantalla podés elegir Claro, Oscuro o Como el dispositivo. Desde Mi cuenta también podés Cerrar sesión.'],
    }],
  },
  notificaciones: {
    descripcion: 'Los avisos informan novedades relacionadas con los eventos y las actividades de tu área.',
    secciones: [{
      titulo: 'Consultar los avisos',
      pasos: ['Seleccioná la campana del encabezado o ingresá a Notificaciones.', 'Revisá las notificaciones recientes. Podés alternar entre Sin leer y Todas.', 'Abrí un aviso para consultar su destino. Al abrirlo queda marcado como leído.'],
      notas: ['Marcar todas como leídas permite limpiar los avisos pendientes.', 'Los avisos y sus destinos dependen de tu perfil. Los de Cocina llevan a su vista de cocina.'],
    }],
  },
};
