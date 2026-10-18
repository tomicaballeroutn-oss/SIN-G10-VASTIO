/*
 * Sistema de diseño de Vastio, portado a TypeScript desde design/design-system.
 * Las pantallas usan solo estos componentes. Si falta uno, se agrega acá; no se improvisa en la pantalla.
 * Los estilos se cargan una vez con `import './ds/ds.css'` en main.tsx.
 */
export * from './tipos';
export { ESTADOS, ESTADO_POR_CODIGO, ESTADOS_DEL_EVENTO, SALONES, ORDEN_SALONES } from './estados';
export { Icon, type IconProps } from './components/Icon';
export { Button, IconButton, type ButtonProps, type IconButtonProps } from './components/Button';
export {
  Input, Select, Checkbox, Switch, Stepper, Combobox, SelectorArchivos, CantidadEnCajas,
  type InputProps, type SelectProps, type SelectOption, type ChoiceProps, type StepperProps, type ComboboxProps, type ComboboxOption,
  type SelectorArchivosProps, type CantidadEnCajasProps,
} from './components/formularios';
export { LectorCodigo, type LectorCodigoProps, type IniciarLector, type ControlLector } from './components/lector';
export { cantidadLegible, enBultos, plural } from './cantidad';
export { Alert, Dialog, EmptyState, Badge, type AlertProps, type DialogProps, type EmptyStateProps, type BadgeProps } from './components/feedback';
export { StatusChip, SalonTag, type StatusChipProps, type SalonTagProps } from './components/estado';
export {
  Card, Table, Tabs, Actor, Stat, Timeline,
  type CardProps, type TableColumn, type TableProps, type TabItem, type TabsProps, type ActorProps, type StatProps, type TimelineItem,
} from './components/datos';
export { Nav, type NavItem, type NavProps } from './components/Nav';
export { Logo, type LogoProps } from './components/Logo';
export { AgendaGrid, EventCard, type AgendaEvent, type AgendaGridProps, type EventCardProps } from './components/agenda';
export { StockLevel, MovementCard, type StockLevelProps, type MovementCardProps } from './components/stock';
export { aplicarTema, temaGuardado, type Tema } from './tema';
export { tamanoLegible } from './util';
