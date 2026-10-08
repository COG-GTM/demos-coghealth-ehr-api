-- V5__cost_center_restructure_2026_10.sql
-- Workday cost center restructure effective 2026-10-01 (first seen in workday_pto_export_20261001.csv):
-- perioperative cost centers moved from CC-200-* to CC-230-* (Main OR, Ambulatory Surgery) and
-- CC-240-* (Cardiac OR, Endoscopy). EHR departments are unchanged. Old rows are kept and end-dated.

UPDATE department_cost_center_map
   SET effective_to = DATE '2026-09-30'
 WHERE cost_center IN ('CC-200-5410', 'CC-200-5411', 'CC-200-5412', 'CC-200-5420')
   AND effective_to IS NULL;

INSERT INTO department_cost_center_map (cost_center, department_id, department_name, effective_from, effective_to) VALUES
('CC-230-5410', 2130, 'Main OR - Tower', '2026-10-01', NULL),
('CC-230-5411', 2131, 'Ambulatory Surgery - North', '2026-10-01', NULL),
('CC-240-5412', 2132, 'Cardiac OR', '2026-10-01', NULL),
('CC-240-5420', 2140, 'Endoscopy Suite', '2026-10-01', NULL);
