alter table outbox_events add column claimed_on datetime(6);

-- Supports OutboxEventStore#reclaimStuckInProgress (M3): find rows stuck IN_PROGRESS past a grace
-- period after an app crash between claim and mark-outcome.
create index idx_outbox_events_status_claimed_on on outbox_events (status, claimed_on);

-- Supports OutboxMaintenanceScheduler#purgeSentEvents (M4): bound outbox_events growth by purging
-- old SENT rows.
create index idx_outbox_events_status_sent_on on outbox_events (status, sent_on);
