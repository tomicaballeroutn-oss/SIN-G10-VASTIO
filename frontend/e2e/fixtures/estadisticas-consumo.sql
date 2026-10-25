-- SOLO para una base descartable recién migrada y con usuarios demo. No ejecutar sobre datos reales.
BEGIN;
INSERT INTO bebida(nombre,presentacion,tipo_bebida_id,unidad_manipulacion_id,unidades_por_bulto,activo)
VALUES ('E2E B501 Fernet','750 ml',3,1,6,true), ('E2E B501 Gin','750 ml',3,1,6,true);
WITH cliente_nuevo AS (
  INSERT INTO cliente(nombre) VALUES ('Cliente sintético B501') RETURNING cliente_id
), unidades AS (
  INSERT INTO unidad_comercializable(salon_id,fecha,turno_id)
  VALUES (1,'2038-05-01',2),(1,'2038-05-02',2),(2,'2038-05-03',2),(1,'2038-05-04',2)
  RETURNING unidad_id, fecha
)
INSERT INTO evento(codigo,unidad_id,cliente_id,tipo_evento_id,vendedora_id,planner_id,estado,nombre,
    cantidad_invitados,invitados_definitivos,importe_sena,fecha_sena,firmante_dni,fecha_firma_contrato,version)
SELECT 'EV-2038-0000' || extract(day from u.fecha)::text, u.unidad_id, c.cliente_id,
    CASE WHEN extract(day from u.fecha)=3 THEN 4 ELSE 2 END,
    (SELECT usuario_id FROM usuario WHERE nombre_usuario='lucia.ferreyra'),
    (SELECT usuario_id FROM usuario WHERE nombre_usuario='ana.sosa'),
    CASE WHEN extract(day from u.fecha)=4 THEN 'REALIZADO' ELSE 'CERRADO' END,
    'E2E B501 ' || extract(day from u.fecha)::text,
    CASE extract(day from u.fecha) WHEN 1 THEN 100 WHEN 2 THEN 200 WHEN 3 THEN NULL ELSE 100 END,
    true,100,'2038-04-01','30111222','2038-04-10',0
FROM unidades u CROSS JOIN cliente_nuevo c;
INSERT INTO consumo_evento(evento_id,bebida_id,cantidad_entregada,cantidad_devuelta,cantidad_remanente,cantidad_consumida)
SELECT e.evento_id,b.bebida_id,v.cantidad,0,0,v.cantidad
FROM (VALUES ('E2E B501 1','E2E B501 Fernet',10.0),('E2E B501 1','E2E B501 Gin',2.0),
             ('E2E B501 3','E2E B501 Fernet',9.0),('E2E B501 4','E2E B501 Fernet',999.0)) v(evento,bebida,cantidad)
JOIN evento e ON e.nombre=v.evento JOIN bebida b ON b.nombre=v.bebida;
COMMIT;
