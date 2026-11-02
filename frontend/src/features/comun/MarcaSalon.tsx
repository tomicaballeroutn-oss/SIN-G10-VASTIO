import type { SalonId } from '../../ds';
import './marca-salon.css';

/** Marca decorativa del salón, con variantes legibles para ambos temas. */
export function MarcaSalon({ salon, className = '' }: { salon: SalonId; className?: string }) {
  return (
    <span className={`marca-salon marca-salon--${salon} ${className}`} aria-hidden="true">
      <img className="marca-salon__imagen" src={`/brand/salones/${salon}.png`} alt="" />
      {salon === 'avril' && <img className="marca-salon__texto-oscuro" src="/brand/salones/avril.png" alt="" />}
    </span>
  );
}
