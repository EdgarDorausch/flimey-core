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

package modules.subject.model

import java.sql.Timestamp

/**
 * A Frame represents the accumulation of several [[modules.subject.model.Subject Subjects]].
 * In other words, a Frame is a Subject tree node.
 * <p> Has a repository representation.
 *
 * @param id       unique identifier (primary key)
 * @param entityId id of the parent [[modules.core.model.FlimeyEntity FlimeyEntity]]
 * @param typeVersionId   if of the parent [[modules.core.model.TypeVersion TypeVersion]]
 * @param status   progress status
 * @param created  creation time
 */
case class Frame(id: Long, entityId: Long, typeVersionId: Long, status: SubjectState.State, created: Timestamp)

object Frame {

  def applyRaw(id: Long, entityId: Long, typeVersionId: Long, status: String, created: Timestamp): Frame = {
    Frame(id, entityId, typeVersionId, SubjectState.withName(status), created)
  }

  def unapplyToRaw(arg: Frame): Option[(Long, Long, Long, String, Timestamp)] =
    Option((arg.id, arg.entityId, arg.typeVersionId, arg.status.toString, arg.created))

  val tupledRaw: ((Long, Long, Long, String, Timestamp)) => Frame = (this.applyRaw _).tupled

}