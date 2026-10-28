-- The retired work-order batch/path model is not used by the current production flow.
-- Drop child records first; IF EXISTS also handles environments where either table
-- was removed before this version was applied.
DROP TABLE IF EXISTS batch_route_node;
DROP TABLE IF EXISTS production_batch;
