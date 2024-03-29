/*
 * This file is part of the flimey-core software.
 * Copyright (C) 2021 Karl Kegel
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

package modules.subject.repository

import java.sql.Timestamp

import modules.subject.model.Subject
import slick.jdbc.PostgresProfile.api._

class SubjectTable(tag: Tag) extends Table[Subject](tag, "subject") {

  def id = column[Long]("id", O.PrimaryKey,O.AutoInc)
  def entityId = column[Long]("entity_id")
  def frameId = column[Long]("frame_id")
  def typeVersionId = column[Long]("type_version_id")
  def state = column[String]("state")
  def created = column[Timestamp]("created")

  override def * = (id, entityId, frameId, typeVersionId, state, created) <> (Subject.tupledRaw, Subject.unapplyToRaw)

}
