CREATE TABLE tells(
messageId int AUTO_INCREMENT PRIMARY KEY,
sender varchar(55),
target varchar(55),
message varchar(255) CHARACTER SET utf8 COLLATE utf8_unicode_ci,
sent timestamp default CURRENT_TIMESTAMP
);