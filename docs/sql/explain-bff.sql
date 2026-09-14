-- Inspection only. Run with psql -v account_id=101 -f docs/sql/explain-bff.sql
-- Requires DB credentials supplied externally. No indexes or data changes.
BEGIN READ ONLY;
EXPLAIN (ANALYZE, BUFFERS)
SELECT cuenta_id,nombre,saldo_original,tasa,saldo_procesado,tipo
FROM interes_procesado WHERE cuenta_id = :account_id ORDER BY id DESC LIMIT 1;
EXPLAIN (ANALYZE, BUFFERS)
SELECT saldo_procesado,tipo FROM interes_procesado
WHERE cuenta_id = :account_id ORDER BY id DESC LIMIT 1;
EXPLAIN (ANALYZE, BUFFERS)
SELECT saldo_procesado FROM interes_procesado
WHERE cuenta_id = :account_id ORDER BY id DESC LIMIT 1;
EXPLAIN (ANALYZE, BUFFERS)
SELECT EXISTS (SELECT 1 FROM interes_procesado WHERE cuenta_id = :account_id);
EXPLAIN (ANALYZE, BUFFERS)
SELECT cuenta_id,fecha,tipo_transaccion,monto,descripcion FROM movimiento_anual_procesado
WHERE cuenta_id = :account_id ORDER BY fecha DESC,id DESC LIMIT 20;
EXPLAIN (ANALYZE, BUFFERS)
SELECT fecha,tipo_transaccion,monto FROM movimiento_anual_procesado
WHERE cuenta_id = :account_id ORDER BY fecha DESC,id DESC LIMIT 5;
EXPLAIN (ANALYZE, BUFFERS)
SELECT tipo_transaccion,monto FROM movimiento_anual_procesado
WHERE cuenta_id = :account_id ORDER BY fecha DESC,id DESC LIMIT 3;
EXPLAIN (ANALYZE, BUFFERS)
SELECT transaccion_id,fecha,monto,tipo,anomalia FROM transaccion_procesada
WHERE anomalia = TRUE ORDER BY fecha DESC,id DESC LIMIT 10;
ROLLBACK;
