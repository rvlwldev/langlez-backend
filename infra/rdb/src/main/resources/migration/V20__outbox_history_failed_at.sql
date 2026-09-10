alter table chat_event_outbox_history add column if not exists failed_at timestamp with time zone;
alter table echo_event_outbox_history add column if not exists failed_at timestamp with time zone;
alter table member_outbox_history add column if not exists failed_at timestamp with time zone;
alter table follow_event_outbox_history add column if not exists failed_at timestamp with time zone;
alter table block_event_outbox_history add column if not exists failed_at timestamp with time zone;
