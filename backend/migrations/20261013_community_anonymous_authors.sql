-- MySQL 8: stop old application instances and back up the database first.
-- No existing comments are modified. Backfill is atomic per post on first access.
-- Deployment notes: 20261013_community_anonymous_authors.md
CREATE TABLE IF NOT EXISTS COMMUNITY_ANONYMOUS_AUTHOR (
    anonymous_author_id BIGINT NOT NULL AUTO_INCREMENT,
    post_id BIGINT NOT NULL,
    author_id BIGINT NOT NULL,
    anonymous_number BIGINT NOT NULL,
    PRIMARY KEY (anonymous_author_id),
    CONSTRAINT uk_anonymous_post_author UNIQUE (post_id, author_id),
    CONSTRAINT uk_anonymous_post_number UNIQUE (post_id, anonymous_number),
    CONSTRAINT fk_anonymous_post FOREIGN KEY (post_id) REFERENCES COMMUNITY_POST (post_id),
    CONSTRAINT fk_anonymous_author FOREIGN KEY (author_id) REFERENCES `USER` (user_id)
) ENGINE=InnoDB;
