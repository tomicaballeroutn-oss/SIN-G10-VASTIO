-- Asistencia prevista por segmento (docs/sprint-4.md, decisiones 1 y 2). En egresados, la cantidad de invitados son los
-- comensales de la cena: el segmento que la representa no se carga aparte. Solo uno puede tener la marca.
ALTER TABLE tipo_segmento_asistencia ADD COLUMN equivale_invitados boolean NOT NULL DEFAULT false;
UPDATE tipo_segmento_asistencia SET equivale_invitados = true WHERE tipo_segmento_id = 1;
CREATE UNIQUE INDEX ux_segmento_equivale_invitados ON tipo_segmento_asistencia (equivale_invitados) WHERE equivale_invitados;
