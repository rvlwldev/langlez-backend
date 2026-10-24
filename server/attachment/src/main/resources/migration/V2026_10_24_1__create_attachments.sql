create table attachments (
    id           uuid         primary key,
    uploader_id  uuid         not null,
    source       varchar(50)  not null,
    type         varchar(20)  not null,
    batch_id     uuid         not null,
    position     integer      not null,
    object_key   varchar(255) not null,
    content_type varchar(100) not null,
    size_bytes   bigint       not null,
    status       varchar(20)  not null,
    created_at   timestamptz  not null,
    uploaded_at  timestamptz,
    constraint uk_attachments_object_key unique (object_key)
);

create index idx_attachments_batch_position on attachments (batch_id, position);
create index idx_attachments_uploader_created on attachments (uploader_id, created_at);
