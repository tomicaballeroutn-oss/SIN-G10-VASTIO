import { screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { _reiniciarCliente, type Rol } from '../../api/cliente';
import type { EstadoCalendar } from '../../api/calendar';
import { backendFalso, json, montar, pedidos, problema, sesionDe } from '../../test/backendFalso';
import { fichaDePrueba } from '../../test/fichas';

const pendiente: EstadoCalendar = {
  eventoId: 5, codigo: 'EV-2026-00123', salon: 'Avril', fecha: '2032-12-31', turno: 'Noche',
  estado: 'PENDIENTE', pendiente: 'EXPORTAR', error: null, ultimoExito: null, configurado: true,
  puedeExportar: true, puedeRetirar: false,
};
const exportado: EstadoCalendar = { ...pendiente, estado: 'EXPORTADO', pendiente: 'NINGUNA', puedeRetirar: true };
beforeEach(_reiniciarCliente);
afterEach(() => { vi.unstubAllGlobals(); vi.restoreAllMocks(); });
function backend(estado: EstadoCalendar, roles: Rol[] = ['DIRECCION'], operar: () => Response | Promise<Response> = () => json(200, exportado)) {
  let actual = estado;
  return backendFalso(sesionDe(roles), async p => {
    if (p.ruta === '/eventos/5') return json(200, fichaDePrueba());
    if (p.ruta === '/eventos/5/calendar') {
      if (p.metodo === 'GET') return json(200, actual);
      const respuesta = await operar();
      if (respuesta.ok) actual = await respuesta.clone().json() as EstadoCalendar;
      return respuesta;
    }
    if (p.ruta === '/calendar/pendientes') return json(200, [actual]);
    return undefined;
  });
}
describe('M-06 · Calendar manual', () => {
  it.each<Rol>(['DIRECCION','COORDINACION'])('publica con botón rojo y muestra Exportado sin otra escritura: %s', async rol => {
    const fetch = backend(pendiente,[rol]); montar('/eventos/5');
    const boton = await screen.findByRole('button',{ name: 'Exportar a Google Calendar' });
    expect(boton.className).toContain('danger');
    await userEvent.click(boton);
    expect(await screen.findByText('✓ Exportado')).toBeInTheDocument();
    expect(screen.queryByRole('button',{name:'Exportar a Google Calendar'})).not.toBeInTheDocument();
    expect(pedidos(fetch).filter(p=>p.ruta.endsWith('/calendar')&&p.metodo==='POST')).toHaveLength(1);
  });
  it.each<Rol>(['ADMINISTRACION','VENDEDORA','PLANNER','COMPRAS'])('no ofrece acciones ni consulta Calendar para %s', async rol => {
    const fetch=backend(pendiente,[rol]); montar('/eventos/5');
    await screen.findByRole('heading',{name:'Bruno y Martina'});
    expect(screen.queryByRole('heading',{name:'Google Calendar'})).not.toBeInTheDocument();
    expect(pedidos(fetch).filter(p=>p.ruta.endsWith('/calendar'))).toHaveLength(0);
  });
  it('actualiza una publicación desactualizada mediante PUT', async()=>{
    const fetch=backend({...exportado,pendiente:'ACTUALIZAR'}); montar('/eventos/5');
    await userEvent.click(await screen.findByRole('button',{name:'Actualizar en Google Calendar'}));
    await screen.findByText('✓ Exportado');
    expect(pedidos(fetch).some(p=>p.metodo==='PUT'&&p.ruta.endsWith('/calendar'))).toBe(true);
  });
  it('retira manualmente y refleja la confirmación del backend', async()=>{
    const fetch=backend({...exportado,pendiente:'RETIRAR',puedeExportar:false},['COORDINACION'],()=>json(200,{...exportado,estado:'RETIRADO',puedeExportar:false,puedeRetirar:false}));
    montar('/eventos/5');
    await userEvent.click(await screen.findByRole('button',{name:'Retirar de Google Calendar'}));
    expect(await screen.findByText('Publicación retirada')).toBeInTheDocument();
    expect(pedidos(fetch).some(p=>p.metodo==='DELETE'&&p.ruta.endsWith('/calendar'))).toBe(true);
  });
  it('desactivada conserva el historial sin ofrecer acciones', async()=>{
    const fetch=backend({...pendiente,configurado:false}); montar('/eventos/5');
    expect(await screen.findByText('Integración desactivada')).toBeInTheDocument();
    expect(screen.queryByRole('button',{name:'Exportar a Google Calendar'})).not.toBeInTheDocument();
    expect(screen.getByText(/El historial se conserva/)).toBeInTheDocument();
    expect(pedidos(fetch).every(p=>p.metodo==='GET')).toBe(true);
  });
  it('error y reintento seguro sin renovar el login por un fallo Google', async()=>{
    let intentos=0;
    const fetch=backend(pendiente,['DIRECCION'],()=>++intentos===1?problema(503,'RED_GOOGLE','Resultado incierto. Verificá y reintentá.'):json(200,exportado));
    montar('/eventos/5'); await userEvent.click(await screen.findByRole('button',{name:'Exportar a Google Calendar'}));
    expect(await screen.findByText('Resultado incierto. Verificá y reintentá.')).toBeInTheDocument();
    await userEvent.click(await screen.findByRole('button',{name:'Exportar a Google Calendar'}));
    await screen.findByText('✓ Exportado');
    expect(intentos).toBe(2);
    expect(fetch.mock.calls.filter(([url])=>url.endsWith('/auth/refresh'))).toHaveLength(1);
  });
  it('bloquea doble clic mientras espera confirmación', async()=>{
    let terminar!: (r:Response)=>void;
    const fetch=backend(pendiente,['DIRECCION'],()=>new Promise(r=>{terminar=r;}));
    montar('/eventos/5'); await userEvent.dblClick(await screen.findByRole('button',{name:'Exportar a Google Calendar'}));
    expect(pedidos(fetch).filter(p=>p.metodo==='POST')).toHaveLength(1);
    terminar(json(200,exportado)); await screen.findByText('✓ Exportado');
  });
  it('resultado incierto conserva acción y advertencia accesible', async()=>{
    backend({...pendiente,estado:'INCIERTO',error:'RED_GOOGLE'}); montar('/eventos/5');
    expect(await screen.findByText('Resultado incierto: requiere verificación')).toHaveAttribute('role','status');
    expect(screen.getByRole('button',{name:'Exportar a Google Calendar'})).toBeEnabled();
  });
  it('una operación de otro usuario bloquea escrituras y permite volver a consultar', async()=>{
    const fetch=backend({...pendiente,estado:'EN_PROCESO'}); montar('/eventos/5');
    expect(await screen.findByRole('button',{name:'Exportar a Google Calendar'})).toBeDisabled();
    await userEvent.click(screen.getByRole('button',{name:'Volver a consultar publicación'}));
    await vi.waitFor(()=>expect(pedidos(fetch).filter(p=>p.ruta.endsWith('/calendar')&&p.metodo==='GET')).toHaveLength(2));
    expect(pedidos(fetch).filter(p=>p.metodo==='POST')).toHaveLength(0);
  });
  it('lista persistente permite abrir la ficha', async()=>{
    backend(pendiente); montar('/calendar/pendientes');
    const tabla=await screen.findByRole('table',{name:'Pendientes de Google Calendar'});
    expect(within(tabla).getByText('EV-2026-00123')).toBeInTheDocument();
    expect(within(tabla).getByText('Sin exportar')).toBeInTheDocument();
    await userEvent.click(within(tabla).getByRole('button',{name:'Abrir EV-2026-00123'}));
    expect(await screen.findByRole('heading',{name:'Bruno y Martina'})).toBeInTheDocument();
  });
  it('otros perfiles no abren el listado restringido',async()=>{
    const fetch=backend(pendiente,['ADMINISTRACION']); montar('/calendar/pendientes');
    expect(await screen.findByText('Tu perfil no tiene acceso a esta pantalla')).toBeInTheDocument();
    expect(pedidos(fetch).some(p=>p.ruta==='/calendar/pendientes')).toBe(false);
  });
  it('no muestra confirmación vigente mientras hay seguimiento local pendiente',async()=>{
    backend({...exportado,seguimientoPendiente:true,errorSeguimiento:'SEGUIMIENTO_CALENDAR_FALLIDO'});montar('/eventos/5');
    expect(await screen.findByText('Seguimiento local pendiente')).toBeInTheDocument();
    expect(screen.queryByText('✓ Exportado')).not.toBeInTheDocument();
    expect(screen.getByRole('button',{name:'Volver a consultar seguimiento'})).toBeEnabled();
  });
  it('conflicto exige revisar y aceptar el ETag antes de otra escritura',async()=>{
    let actual:EstadoCalendar={...exportado,estado:'ERROR',pendiente:'ACTUALIZAR',error:'CONFLICTO_REMOTO'};
    const fetch=backendFalso(sesionDe(['DIRECCION']),p=>{
      if(p.ruta==='/eventos/5') return json(200,fichaDePrueba());
      if(p.ruta==='/eventos/5/calendar') return json(200,actual);
      if(p.ruta==='/eventos/5/calendar/verificacion') return json(200,{etag:'externo-v2',coincideConVastio:false});
      if(p.ruta==='/eventos/5/calendar/reconciliacion') {
        actual={...actual,estado:'PENDIENTE',error:null};return json(200,actual);
      }
      return undefined;
    });
    montar('/eventos/5');
    await userEvent.click(await screen.findByRole('button',{name:'Revisar cambios externos'}));
    expect(screen.queryByRole('button',{name:'Actualizar en Google Calendar'})).not.toBeInTheDocument();
    expect(screen.getByText(/Esta aceptación no modifica Google/)).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button',{name:'Aceptar revisión externa'}));
    expect(await screen.findByRole('button',{name:'Actualizar en Google Calendar'})).toBeEnabled();
    expect(pedidos(fetch).some(p=>p.metodo==='PUT'||p.metodo==='DELETE')).toBe(false);
    expect(pedidos(fetch).find(p=>p.ruta.endsWith('/reconciliacion'))?.cuerpo).toEqual({etag:'externo-v2'});
  });
});
