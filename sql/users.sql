CREATE DATABASE users;

CREATE TABLE users(
id int(10) AUTO_INCREMENT PRIMARY KEY,
name varchar(55),
wiki varchar(55),
created timestamp default CURRENT_TIMESTAMP
);