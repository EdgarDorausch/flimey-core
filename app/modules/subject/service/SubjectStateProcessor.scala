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

package modules.subject.service

import modules.subject.model.SubjectState
import modules.util.messages.{ERR, OK, Status}

/**
 * Trait which provides functionality for parsing and processing the [[modules.subject.model.SubjectState SubjectState]].
 */
trait SubjectStateProcessor extends SuperSubjectStateProcessor {

  override def isValidStateTransition(oldState: SubjectState.State, newState: SubjectState.State): Status = {
    if(newState == SubjectState.CREATED) return ERR("This state can not be entered again")
    if(newState == SubjectState.ARCHIVED) return ERR("Subjects can not be archived independently from their Frame")
    OK()
  }

}
