create table outbox_events (
                                id bigint not null auto_increment,
                                aggregate_type varchar(64) not null,
                                aggregate_id bigint not null,
                                topic varchar(255) not null,
                                payload text not null,
                                status varchar(16) not null,
                                attempts int not null default 0,
                                created_on datetime(6) not null,
                                sent_on datetime(6),
                                version bigint not null default 0,
                                primary key (id)) engine=InnoDB;

create index idx_outbox_events_status_created_on on outbox_events (status, created_on);
