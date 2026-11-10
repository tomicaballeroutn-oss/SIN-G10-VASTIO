# Login animado («Telón») · UI-01

Propuesta aprobada para la pantalla de inicio de sesión. Se ve abriendo `vista-previa.html` en el navegador (doble clic).

## Qué pasa en pantalla (≈ 3,2 s)

1. Fondo azul (`--deep`) y en el centro la marca animada en blanco (la V se arma y cae la estrellita), a 1,8× de velocidad.
2. Debajo aparecen **VASTIO** (Jost, versales con espaciado ancho) y **«Todo a la vista.»**
3. El azul se retira con un borde curvo:
   - **Escritorio (≥ 960 px):** se corre a la izquierda y queda ocupando ~47 % con el borde derecho curvo; la marca queda centrada en ese lado. El formulario aparece a la derecha.
   - **Teléfono y tableta:** sube y queda arriba (300 px) con el borde inferior curvo; la marca queda chica en el centro. El formulario aparece abajo.
4. El formulario aparece sobre el fondo crema, **sin ficha ni recuadro**.

Con «reducir movimiento» todo aparece ya armado (y `AnimacionMarca` ya muestra la marca quieta).

## Archivos de esta carpeta

- `login-animado.css`: los estilos y la animación, con las clases que va a usar `PaginaIngreso`. Reemplaza el bloque UI-01 de `frontend/src/features/sesion/sesion.css` (el bloque de UI-02, sesión vencida, queda como está).
- `vista-previa.html`: la referencia visual, en HTML plano. No se copia al código.

La animación ya está en el repo: `frontend/public/brand/animaciones/vastio-logo-animado-blanco.webm` (y `.gif` para Safari), mostrada con `AnimacionMarca`.

## Cambios en el código

### 1. `AnimacionMarca` (`frontend/src/ds/components/animacion.tsx`)
Agregar una prop opcional `velocidad?: number` (por defecto 1). En el `<video>`, aplicarla con `playbackRate` y `defaultPlaybackRate` (por ejemplo con un `ref` o en `onLoadedMetadata`). Donde se cuenta la duración (GIF o marca quieta), usar `DURACION_ANIMACION_MS / velocidad`. El GIF de Safari no se puede acelerar: ahí se ve a velocidad normal y está bien.

### 2. `PaginaIngreso` (`frontend/src/features/sesion/PaginaIngreso.tsx`)
Estructura nueva (mantener toda la lógica actual de sesión, `volver` y redirección):

```tsx
<main className="pagina-ingreso">
  <div className="pagina-ingreso__telon" aria-hidden />
  <div className="pagina-ingreso__marca" aria-hidden>
    <span className="pagina-ingreso__logo"><AnimacionMarca tono="blanco" tamano={300} velocidad={1.8} /></span>
    <p className="pagina-ingreso__nombre">VASTIO</p>
    <p className="pagina-ingreso__bajada">Todo a la vista.</p>
  </div>
  <div className="pagina-ingreso__lado">
    <div className="pagina-ingreso__form">
      <div className="pagina-ingreso__titulos">
        <p className="overline v-muted">Inicio de sesión</p>
        <h1 className="h1">Ingresá a tu cuenta</h1>
      </div>
      {estado !== 'cargando' && <FormularioIngreso … />}
    </div>
  </div>
</main>
```

- Ya no se usa `Logo` en esta pantalla.
- No va ningún texto de ayuda del tipo «Usá el usuario y la contraseña que te dieron…».
- La pantalla siempre se ve en tema claro (crema + azul), aunque la persona tenga el oscuro: poner `data-theme="light"` en el `<main>`.

### 3. Botón de ojito en la contraseña (sistema de diseño)
En `Input` (`frontend/src/ds/components/formularios.tsx`), cuando `type="password"`, mostrar un botón de ícono dentro del campo, a la derecha:

- Alterna entre ocultar y mostrar (cambia el `type` del input entre `password` y `text`). Es opcional para la persona: arranca oculta.
- Ícono `eye` cuando está oculta y `eye-off` cuando se ve (los dos ya están en `iconos.ts`).
- Es un `<button type="button">` con `aria-label` «Mostrar contraseña» / «Ocultar contraseña», `aria-pressed` y `aria-controls` apuntando al input. No debe enviar el formulario ni sacar el foco del campo.
- Tamaño táctil de 44 × 44 px; el input suma padding a la derecha para que el texto no quede debajo del botón.
- Agregar una prop `revelable?: boolean` (por defecto `true` para `type="password"`) por si algún campo no lo quiere.
- Lo usa `FormularioIngreso` sin cambios, y también el diálogo de sesión vencida (UI-02) porque comparte el formulario.

### 4. Tests
- `AnimacionMarca`: aplica la velocidad al video.
- `Input` con contraseña: el botón alterna el tipo, cambia la etiqueta accesible y no envía el formulario.
- `PaginaIngreso`: los tests actuales de ingreso siguen pasando.
