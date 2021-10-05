/*
 * This file is part of the flimey-core software.
 * Copyright (C) 2020-2021 Karl Kegel
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

package modules.core.util

import modules.core.model._
import modules.util.messages.Status

/**
 * Trait which provides functionality for parsing and processing [[modules.core.model.Constraint Constraint]]s
 */
trait ConstraintProcessor extends PartialConstraintProcessor {

  /**
   * Checks if a given [[modules.core.model.Constraint Constraint]] is a syntactically correct Constraint of an
   * [[modules.core.model.TypeVersion TypeVersion]] specific subtype. No semantic analysis is done!
   *
   * @param constraint to check
   * @return Status with optional error message
   */
  def isValidConstraint(constraint: Constraint): Status

}
