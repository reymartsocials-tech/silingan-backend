ALTER TABLE users
	ADD last_selected_community_id UUID;

ALTER TABLE users
	ADD CONSTRAINT FK_USERS_ON_LAST_SELECTED_COMMUNITY FOREIGN KEY (last_selected_community_id) REFERENCES communities (id);

