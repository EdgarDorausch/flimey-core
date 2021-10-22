/*
 * This file is part of the flimey-core software.
 * Copyright (C) 2020-2021 Karl Kegel
 * Copyright (C) 2021 Edgar Dorausch
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 * */

-- !Ups

create table auth_session (
    id SERIAL NOT NULL PRIMARY KEY,
    session VARCHAR(255) NOT NULL,
    role VARCHAR(255) NOT NULL,
    status BOOL NOT NULL,
    user_id BIGINT NOT NULL,
    created TIMESTAMP NOT NULL
);

create table access (
    id SERIAL NOT NULL PRIMARY KEY,
    session_id BIGINT NOT NULL,
    group_id BIGINT NOT NULL,
    group_name VARCHAR(255) NOT NULL,
    role VARCHAR(255) NOT NULL,
    FOREIGN KEY(session_id) REFERENCES auth_session(id) ON DELETE CASCADE
);

-- !Downs

DROP TABLE access;
DROP TABLE auth_session;