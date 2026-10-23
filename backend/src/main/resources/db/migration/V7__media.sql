-- T17a: uploaded images (the files live in the MediaStorage, only the metadata is here).

CREATE TABLE media (
    id         uuid         NOT NULL,
    owner_id   bigint       NOT NULL,
    purpose    varchar(30)  NOT NULL,
    status     varchar(20)  NOT NULL,
    width      integer      NOT NULL,
    height     integer      NOT NULL,
    bytes      bigint       NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    CONSTRAINT pk_media PRIMARY KEY (id),
    CONSTRAINT fk_media_owner FOREIGN KEY (owner_id) REFERENCES users (id)
);

CREATE INDEX idx_media_owner_created ON media (owner_id, created_at DESC);
