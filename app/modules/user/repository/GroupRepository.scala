/*
 * This file is part of the flimey-core software.
 * Copyright (C) 2020  Karl Kegel
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

package modules.user.repository

import com.google.inject.Inject
import modules.core.repository.ViewerTable
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import slick.jdbc.JdbcProfile
import slick.jdbc.PostgresProfile.api._
import slick.lifted.TableQuery
import modules.user.model.Group
import play.db.NamedDatabase

import scala.concurrent.{ExecutionContext, Future}

/**
 * DB interface for Groups.
 * Provided methods are UNSAFE and must only be used by service classes!
 *
 * @param dbConfigProvider injected db config
 * @param executionContext future execution context
 */
class GroupRepository @Inject()(@NamedDatabase("flimey_data") protected val dbConfigProvider: DatabaseConfigProvider)(
  implicit executionContext: ExecutionContext) extends HasDatabaseConfigProvider[JdbcProfile] {

  val groups = TableQuery[GroupTable]
  val groupMemberships = TableQuery[GroupMembershipTable]
  val groupViewers = TableQuery[GroupViewerTable]
  val entityViewers = TableQuery[ViewerTable]
  /**
   * Add a new Group.
   * The id must be set to 0 to enable auto increment.
   *
   * @param group new Group entity
   * @return new id
   */
  def add(group: Group): Future[Long] = {
    db.run((groups returning groups.map(_.id)) += group)
  }

  /**
   * Get all groups.
   * There should be relatively few groups, so this should not introduce a
   * performance issue.
   * <br />
   * Maybe worth a FIXME
   *
   * @return all Groups
   */
  def getAll: Future[Seq[Group]] = db.run(groups.sortBy(_.id).result)

  /**
   * Get a Group by their id.
   *
   * @return specified Group
   */
  def getById(groupId: Long): Future[Option[Group]] = db.run(groups.filter(_.id === groupId).result.headOption)

  /**
   * Get a Group by its unique name.
   *
   * @return specified Group
   */
  def getByName(groupName: String): Future[Option[Group]] = db.run(groups.filter(_.name === groupName).result.headOption)

  /**
   * Get all Groups of a single User.
   *
   * @param userId id of the User
   * @return groups the User is member of
   */
  def getAllOfUser(userId: Long): Future[Seq[Group]] = {
    db.run((for {
      (c, s) <- groupMemberships.filter(_.userId === userId).sortBy(_.id) join groups on (_.groupId === _.id)
    } yield (c, s)).result).map(_.map(_._2))
  }

  /**
   * Delete an existing Group.
   * This operation deletes also all associated GroupMemberships
   * and all associated AssetViewers.
   *
   * @param id of the Group to be deleted
   * @return Unit
   */
  def delete(id: Long): Future[Unit] = {
    db.run((for {
      _ <- groupViewers.filter(_.viewerId === id).delete
      _ <- groupViewers.filter(_.targetId === id).delete
      _ <- entityViewers.filter(_.viewerId === id).delete
      _ <- groupMemberships.filter(_.groupId === id).delete
      _ <- groups.filter(_.id === id).delete
    } yield ()).transactionally)
  }

  /**
   * Update an existing Group.
   *
   * @param group Group entity to update
   * @return result future
   */
  def update(group: Group): Future[Int] = {
    db.run(groups.filter(_.id === group.id).update(group))
  }

}
