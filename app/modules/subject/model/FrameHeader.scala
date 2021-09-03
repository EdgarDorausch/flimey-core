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

import modules.core.model.{EntityType, Property}
import modules.user.model.ViewerCombinator

/**
 * The FrameHeader extends the [[modules.subject.model.Frame Frame]] class how it is commonly used with
 * all of its objectified [[modules.core.model.Property Properties]] and some additional child data.
 *
 * @param frame   Frame head (contains only id and type reference)
 * @param subjects child [[modules.subject.model.SubjectHeader SubjectHeaders]]
 * @param properties   all Properties of the Frame
 * @param viewers      [[modules.user.model.ViewerCombinator ViewerCombinator]] with Viewer rights
 */
case class FrameHeader(frame: Frame,
                            subjects: Seq[SubjectHeader],
                            properties: Seq[Property],
                            viewers: ViewerCombinator,
                            entityType: Option[EntityType])