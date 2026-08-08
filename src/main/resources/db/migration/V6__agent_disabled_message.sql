-- Add a per-tenant, admin-editable message returned on every channel when the
-- assistant is disabled. NULL means "use the built-in default message", so existing
-- rows keep their current behavior without a data backfill.
ALTER TABLE agent_config ADD COLUMN disabled_message text;
