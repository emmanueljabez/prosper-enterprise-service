-- Migration: Normalize payment status values to match database check constraint

UPDATE payments
SET status = 'pending'
WHERE status IN ('processing', 'PROCESSING');
