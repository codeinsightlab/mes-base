-- Execution is identified by its existing primary key id. execution_no was
-- only a server-generated display value; every business reference uses id.
-- V49 already retired the historical trigger which referenced this column.
ALTER TABLE mes_production_execution
 DROP INDEX idx_execution_no,
 DROP COLUMN execution_no;
