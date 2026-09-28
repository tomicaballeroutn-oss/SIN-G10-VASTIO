-- =====================================================================
-- Vastio · V2 · Datos iniciales de configuración
-- Solo catálogos y parámetros. Los usuarios NO se cargan acá:
-- el primer usuario de Dirección lo crea la aplicación al arrancar
-- (perfil dev) o un script de puesta en marcha, para no versionar hashes.
-- =====================================================================

INSERT INTO rol (rol_id, codigo, nombre) VALUES
 (1,'DIRECCION','Dirección'),
 (2,'COORDINACION','Coordinación comercial'),
 (3,'ADMINISTRACION','Administración'),
 (4,'VENDEDORA','Vendedora'),
 (5,'PLANNER','Planner'),
 (6,'COMPRAS','Encargado de compras'),
 (7,'BARRA','Encargada de barra'),
 (8,'COCINA','Cocina');

-- Orden fijo de la agenda: Avril, Club de Campo, Santa Bárbara
INSERT INTO salon (salon_id, codigo, nombre, capacidad, activo) VALUES
 (1,'avril','Avril',NULL,true),
 (2,'club','Club de Campo',NULL,true),
 (3,'santa-barbara','Santa Bárbara',NULL,true);

INSERT INTO turno (turno_id, codigo, nombre, hora_inicio, hora_fin, cruza_medianoche) VALUES
 (1,'mediodia','Mediodía','12:00','18:00',false),
 (2,'noche','Noche','20:00','06:00',true);

INSERT INTO tipo_evento (tipo_evento_id, nombre, usa_segmentos, activo) VALUES
 (1,'Casamiento',false,true),
 (2,'Quince',false,true),
 (3,'Corporativo',false,true),
 (4,'Egresados',true,true),
 (5,'Cumpleaños',false,true);

INSERT INTO tipo_segmento_asistencia (tipo_segmento_id, nombre, activo) VALUES
 (1,'Comensales cena',true),
 (2,'Entradas anticipadas',true),
 (3,'Venta en puerta',true);

INSERT INTO categoria_servicio (categoria_id, nombre, orden, visible_en_cocina, requerida_para_confirmar, activo) VALUES
 (1,'Recepción',1,true,false,true),
 (2,'Plato principal',2,true,true,true),
 (3,'Postre',3,true,false,true),
 (4,'After',4,true,false,true),
 (5,'Menús especiales',5,true,false,true),
 (6,'Bodega',6,false,false,true),
 (7,'Tipo de barra',7,false,true,true),
 (8,'Técnica',8,false,false,true),
 (9,'Mobiliario',9,false,false,true),
 (10,'Extras',10,false,false,true);

INSERT INTO motivo (motivo_id, ambito, nombre, activo) VALUES
 (1,'CANCELACION','Desistimiento del cliente',true),
 (2,'CANCELACION','Falta de pago',true),
 (3,'CANCELACION','Otro',true),
 (4,'REPROGRAMACION','Pedido del cliente',true),
 (5,'REPROGRAMACION','Necesidad del salón',true),
 (6,'REPROGRAMACION','Otro',true),
 (7,'BLOQUEO','Mantenimiento',true),
 (8,'BLOQUEO','Feriado',true),
 (9,'BLOQUEO','Evento propio',true),
 (10,'AJUSTE','Error de carga',true),
 (11,'AJUSTE','Rotura',true),
 (12,'AJUSTE','Recuento físico',true),
 (13,'AJUSTE','Otro',true);

INSERT INTO parametro (clave, valor, descripcion) VALUES
 ('HORIZONTE_COCINA_DIAS','15','Días hacia adelante que muestra la vista de cocina'),
 ('MINUTOS_EXPIRACION_SESION','60','Minutos de inactividad antes de cerrar la sesión'),
 ('MINUTOS_AVISO_EXPIRACION','5','Minutos de anticipación del aviso de sesión por vencer');

INSERT INTO tipo_bebida (tipo_bebida_id, nombre) VALUES
 (1,'Vino'),(2,'Espumante'),(3,'Destilado'),(4,'Aperitivo'),(5,'Cerveza'),(6,'Gaseosa'),(7,'Agua');

INSERT INTO unidad_manipulacion (unidad_manipulacion_id, nombre) VALUES
 (1,'Cajón'),(2,'Pack'),(3,'Botella');

-- Ubicaciones mínimas: el depósito principal y una barra por salón.
-- Sectores y depósito de transición se configuran desde la aplicación (UI-26).
INSERT INTO ubicacion (ubicacion_id, nombre, tipo, ubicacion_padre_id, salon_id, activo) VALUES
 (1,'Depósito principal','DEPOSITO',NULL,NULL,true),
 (2,'Barra Avril','BARRA',NULL,1,true),
 (3,'Barra Club de Campo','BARRA',NULL,2,true),
 (4,'Barra Santa Bárbara','BARRA',NULL,3,true);

-- Las identidades arrancan después de los valores cargados a mano
SELECT setval(pg_get_serial_sequence('rol','rol_id'), 8);
SELECT setval(pg_get_serial_sequence('salon','salon_id'), 3);
SELECT setval(pg_get_serial_sequence('turno','turno_id'), 2);
SELECT setval(pg_get_serial_sequence('tipo_evento','tipo_evento_id'), 5);
SELECT setval(pg_get_serial_sequence('tipo_segmento_asistencia','tipo_segmento_id'), 3);
SELECT setval(pg_get_serial_sequence('categoria_servicio','categoria_id'), 10);
SELECT setval(pg_get_serial_sequence('motivo','motivo_id'), 13);
SELECT setval(pg_get_serial_sequence('tipo_bebida','tipo_bebida_id'), 7);
SELECT setval(pg_get_serial_sequence('unidad_manipulacion','unidad_manipulacion_id'), 3);
SELECT setval(pg_get_serial_sequence('ubicacion','ubicacion_id'), 4);
