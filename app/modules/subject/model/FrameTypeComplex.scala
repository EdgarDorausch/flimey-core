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

import modules.core.model.EntityType

/**
 * The FrameTypeComplex is a helper class to wrap several [[modules.subject.model.FrameHeader FrameHeaders]]
 * together with all [[modules.core.model.ExtendedEntityType ExtendedEntityTypes]] which define [[modules.subject.model.Frame Frames]].
 *
 * @param frames   FrameHeaders
 * @param frameTypes all EntityTypes which define Frames
 */
case class FrameTypeComplex(frames: Seq[FrameHeader], frameTypes: Seq[EntityType])