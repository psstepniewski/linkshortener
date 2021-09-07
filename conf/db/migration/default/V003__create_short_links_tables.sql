create table if not exists short_links (
    ordering bigserial not null primary key,
    short_link_id varchar not null unique,
    short_link_domain varchar not null,
    short_link_url varchar not null,
    original_link_url varchar not null,
    tags varchar null,
    created_timestamp timestamp with time zone not null
);

create table if not exists short_link_clicks(
    ordering bigserial not null primary key,
    short_link_id varchar not null references short_links(short_link_id),
    user_agent_header varchar null,
    x_forwarded_for_header varchar null,
    created_timestamp timestamp with time zone not null
)