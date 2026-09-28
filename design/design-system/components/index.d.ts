import * as React from 'react';

export type Tone = 'brand' | 'danger';
export type SalonId = 'avril' | 'club' | 'santa-barbara';
export type EventStatus = 'disponible' | 'prereserva' | 'senado' | 'confirmado' | 'realizado' | 'cancelado' | 'bloqueado';
export type StockStatus = 'ok' | 'bajo' | 'sin-stock';
export type IconName = string;

export interface IconProps { name: IconName; size?: number; strokeWidth?: number; title?: string; className?: string; }
export function Icon(props: IconProps): JSX.Element;

export interface ButtonProps {
  children: React.ReactNode;
  variant?: 'solid' | 'outline' | 'text';
  tone?: Tone;
  size?: 'sm' | 'md' | 'lg';
  icon?: IconName; iconEnd?: IconName;
  block?: boolean; loading?: boolean; disabled?: boolean;
  onClick?: (e: React.MouseEvent) => void; href?: string; type?: 'button' | 'submit'; title?: string; className?: string;
}
export function Button(props: ButtonProps): JSX.Element;

export interface IconButtonProps {
  icon: IconName; label: string;
  variant?: 'outline' | 'text' | 'solid'; tone?: Tone; size?: 'sm' | 'md' | 'lg';
  disabled?: boolean; pressed?: boolean; onClick?: (e: React.MouseEvent) => void; type?: 'button' | 'submit'; className?: string;
}
export function IconButton(props: IconButtonProps): JSX.Element;

interface FieldBase { label?: string; hint?: string; error?: string; optional?: boolean; id?: string; disabled?: boolean; className?: string; }
export interface InputProps extends FieldBase {
  value?: string | number; defaultValue?: string | number; onChange?: (e: React.ChangeEvent<HTMLInputElement | HTMLTextAreaElement>) => void;
  placeholder?: string; name?: string; type?: string; inputMode?: 'text' | 'numeric' | 'decimal' | 'tel' | 'email';
  icon?: IconName; suffix?: string; multiline?: boolean; rows?: number; size?: 'md' | 'lg';
  required?: boolean; readOnly?: boolean; autoComplete?: string;
}
export function Input(props: InputProps): JSX.Element;

export interface SelectProps extends FieldBase {
  options: { value: string; label: string; disabled?: boolean }[];
  value?: string; defaultValue?: string; onChange?: (e: React.ChangeEvent<HTMLSelectElement>) => void;
  placeholder?: string; name?: string; size?: 'md' | 'lg'; required?: boolean;
}
export function Select(props: SelectProps): JSX.Element;

export interface ChoiceProps { label: string; hint?: string; checked?: boolean; defaultChecked?: boolean; onChange?: (e: React.ChangeEvent<HTMLInputElement>) => void; disabled?: boolean; name?: string; id?: string; className?: string; }
export function Checkbox(props: ChoiceProps): JSX.Element;
export function Switch(props: ChoiceProps): JSX.Element;

export interface StepperProps extends FieldBase {
  value?: number; defaultValue?: number; onChange?: (value: number) => void;
  min?: number; max?: number; step?: number; unit?: string;
}
export function Stepper(props: StepperProps): JSX.Element;

export interface AlertProps { tone?: 'info' | 'success' | 'warning' | 'danger'; title?: string; children?: React.ReactNode; action?: React.ReactNode; icon?: IconName; className?: string; }
export function Alert(props: AlertProps): JSX.Element;

export interface DialogProps { open?: boolean; inline?: boolean; title: string; onClose?: () => void; children?: React.ReactNode; actions?: React.ReactNode; id?: string; className?: string; }
export function Dialog(props: DialogProps): JSX.Element | null;

export interface EmptyStateProps { icon?: IconName; title: string; children?: React.ReactNode; action?: React.ReactNode; className?: string; }
export function EmptyState(props: EmptyStateProps): JSX.Element;

export interface BadgeProps { tone?: 'neutral' | 'brand' | 'accent' | 'success' | 'warning' | 'danger' | 'info'; icon?: IconName; children: React.ReactNode; className?: string; }
export function Badge(props: BadgeProps): JSX.Element;

export interface StatusChipProps { status: EventStatus | StockStatus; size?: 'md' | 'sm'; label?: string; className?: string; }
export function StatusChip(props: StatusChipProps): JSX.Element;

export interface SalonTagProps { salon: SalonId; variant?: 'tint' | 'dot'; className?: string; }
export function SalonTag(props: SalonTagProps): JSX.Element;

export interface CardProps {
  children?: React.ReactNode; eyebrow?: string; title?: string; subtitle?: string; actions?: React.ReactNode; footer?: React.ReactNode;
  interactive?: boolean; flush?: boolean; onClick?: (e: React.SyntheticEvent) => void; as?: keyof JSX.IntrinsicElements; className?: string;
}
export function Card(props: CardProps): JSX.Element;

export interface TableColumn<T = any> { key: string; header: string; numeric?: boolean; width?: string; render?: (row: T) => React.ReactNode; }
export interface TableProps<T = any> { columns: TableColumn<T>[]; rows: (T & { id?: string | number })[]; caption?: string; dense?: boolean; onRowClick?: (row: T) => void; empty?: string; className?: string; }
export function Table<T = any>(props: TableProps<T>): JSX.Element;

export interface TabsProps {
  items: { id: string; label: string; icon?: IconName; count?: number }[];
  value?: string; defaultValue?: string; onChange?: (id: string) => void; variant?: 'underline' | 'segmented'; label?: string; className?: string;
}
export function Tabs(props: TabsProps): JSX.Element;

export interface ActorProps { name: string; action?: string; at?: string; size?: 'md' | 'sm'; className?: string; }
export function Actor(props: ActorProps): JSX.Element;

export interface StatProps { label: string; value: string | number; unit?: string; delta?: string; deltaTone?: BadgeProps['tone']; deltaIcon?: IconName; hint?: string; className?: string; }
export function Stat(props: StatProps): JSX.Element;

export interface TimelineItem { title: string; from?: EventStatus; to?: EventStatus; detail?: string; actor?: string; action?: string; at?: string; icon?: IconName; tone?: 'neutral' | 'brand' | 'success' | 'warning' | 'danger' | 'info'; }
export function Timeline(props: { items: TimelineItem[]; className?: string }): JSX.Element;

export interface NavItem { id: string; label: string; icon: IconName; group?: string; badge?: number | string; }
export interface NavProps { items: NavItem[]; value?: string; onSelect?: (id: string) => void; layout?: 'sidebar' | 'bottom'; logo?: React.ReactNode; footer?: React.ReactNode; label?: string; className?: string; }
export function Nav(props: NavProps): JSX.Element;

export interface AgendaEvent { date: string; salon: SalonId; turno: 'mediodia' | 'noche'; status: Exclude<EventStatus, 'cancelado' | 'disponible'>; }
export interface AgendaGridProps { year: number; month: number; events?: AgendaEvent[]; today?: string; selected?: string; onSelectDay?: (isoDate: string) => void; onPrev?: () => void; onNext?: () => void; legend?: boolean; className?: string; }
export function AgendaGrid(props: AgendaGridProps): JSX.Element;

export interface EventCardProps { salon: SalonId; status: EventStatus; tipo: string; title: string; fecha: string; turno: string; invitados?: number; vendedora?: string; planner?: string | null; onOpen?: (props: EventCardProps) => void; className?: string; }
export function EventCard(props: EventCardProps): JSX.Element;

export interface StockLevelProps { name: string; presentacion?: string; ubicacion?: string; cantidad: number; comprometido?: number; unidad?: string; unidadUno?: string; status?: StockStatus; className?: string; }
export function StockLevel(props: StockLevelProps): JSX.Element;

export interface MovementCardProps {
  tipo: 'entrega' | 'devolucion' | 'retiro-adicional' | 'ingreso'; evento?: string; salon?: SalonId; desde?: string; hasta?: string;
  items: { nombre: string; cantidad: number; unidad?: string; unidadUno?: string }[]; nota?: string; actor: string; at?: string; className?: string;
}
export function MovementCard(props: MovementCardProps): JSX.Element;
