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

package modules.news.service

import java.sql.Timestamp
import java.time.Instant

import modules.news.model.{NewsEvent, NewsType}

trait NewsFactory {

  /**
   * Create a new [[modules.news.model.NewsEvent NewsEvent]] from a [[modules.subject.model.Frame Frame]].
   *
   * @param frameId id of the Frame
   * @param newsType     [[modules.news.model.NewsType NewsType]] which triggered the NewsEvent
   * @param description  an optional short description
   * @return NewsEvent
   */
  def eventFrom(frameId: Long, newsType: NewsType.Value, description: Option[String]): NewsEvent = {
    NewsEvent(0, newsType, 1, description.getOrElse(""), NewsRouter.buildRoute(frameId, newsType), Timestamp.from(Instant.now()))
  }

  /**
   * Create a new [[modules.news.model.NewsEvent NewsEvent]] from a [[modules.subject.model.Subject Subject]].
   *
   * @param frameId  id of the parent [[modules.subject.model.Frame Frame]]
   * @param subjectId id of the Subject
   * @param newsType      [[modules.news.model.NewsType NewsType]] which triggered the NewsEvent
   * @param description   an optional short description
   * @return
   */
  def eventFrom(frameId: Long, subjectId: Long, newsType: NewsType.Value, description: Option[String]): NewsEvent = {
    NewsEvent(0, newsType, 1, description.getOrElse(""), NewsRouter.buildRoute(frameId, subjectId, newsType), Timestamp.from(Instant.now()))
  }

}
