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

import modules.asset.model.Asset
import modules.core.model.Property
import modules.user.model.ViewerCombinator

/**
 * The extended [[Collectible]] class how it is commonly used with all its objectified properties.
 *
 * @param collectible Collectible head (contains only id and type reference)
 * @param properties  all SubjectProperties of the Collectible
 * @param attachments all attached Assets of the Collectible
 * @param viewers     ViewerCombinator
 */
case class ExtendedCollectible(collectible: Collectible,
                               properties: Seq[Property],
                               attachments: Seq[Asset],
                               viewers: ViewerCombinator) extends ExtendedSubject
