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

import com.google.inject.Inject
import modules.core.model.{Constraint, Property}
import modules.core.repository._
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import slick.jdbc.JdbcProfile
import slick.jdbc.MySQLProfile.api._
import slick.lifted.TableQuery

import scala.concurrent.{ExecutionContext, Future}

class CollectionRepository @Inject()(protected val dbConfigProvider: DatabaseConfigProvider)(implicit executionContext: ExecutionContext)
  extends HasDatabaseConfigProvider[JdbcProfile] {

  val collections = TableQuery[CollectionTable]
  val entities = TableQuery[FlimeyEntityTable]
  val entityTypes = TableQuery[TypeTable]
  val constraints = TableQuery[ConstraintTable]
  val properties = TableQuery[PropertyTable]
  val viewers = TableQuery[ViewerTable]

  /**
   * Delete a [[modules.core.model.EntityType EntityType]] of a [[modules.subject.model.Collection Collection]].
   * <p> To ensure integrity, this operation deletes:
   * <p> 1. all [[modules.core.model.Constraint Constraints]] of the type.
   * <p> 2. all [[modules.core.model.FlimeyEntity Entities (Collections)]] which use this type...
   * <p> 3. ... with all their [[modules.core.model.Property Properties]].
   * <p> 4. all to Entities of this type associated [[modules.core.model.Viewer Viewers]].
   *
   * @param id of the EntityType (CollectionType) to delete
   * @return Future[Unit]
   */
  def deleteCollectionType(id: Long): Future[Unit] = {
    val entitiesOfType = collections.filter(_.typeId === id).map(_.entityId)
    db.run((for {
      _ <- properties.filter(_.parentId in entitiesOfType).delete
      _ <- viewers.filter(_.targetId in entitiesOfType).delete
      _ <- collections.filter(_.typeId === id).delete
      _ <- entities.filter(_.id in entitiesOfType).delete
      _ <- constraints.filter(_.typeId === id).delete
      _ <- entityTypes.filter(_.id === id).delete
    } yield ()).transactionally)
  }

  /**
   * Add new [[modules.core.model.Constraint Constraints]] to a [[modules.core.model.FlimeyEntity FlimeyEntity]] of
   * the [[modules.subject.model.Collection Collection]] subtype.
   * <p> The id of all new Constraints must be set to 0 to enable auto increment.
   * <p> This method makes a difference between new propertyConstraints (Constraints of the HasProperty type) and other
   * Constraints.
   * <p> <strong>If you add new HasProperty Constraints the wrong way (via otherConstraints or just
   * [[modules.core.repository.ConstraintRepository#addConstraint]]) will lead to loosing the integrity of the type
   * system. </strong>
   *
   * @param typeId              id of the EntityType (of a Collection) to add the new Constraints to.
   * @param propertyConstraints new Constraints of HasProperty type
   * @param otherConstraints    new Constraints NOT of HasProperty type
   * @return Future[Unit]
   */
  def addConstraints(typeId: Long, propertyConstraints: Seq[Constraint], otherConstraints: Seq[Constraint]): Future[Unit] = {

    val allConstraints = propertyConstraints ++ otherConstraints

    db.run((for {
      entityIDsWithType <- collections.filter(_.typeId === typeId).map(_.entityId).result
      _ <- DBIO.sequence(propertyConstraints.map(propertyConstraint => {
        properties ++= entityIDsWithType.map(entityId => Property(0, propertyConstraint.v1, "", entityId))
      }))
      _ <- (constraints returning constraints.map(_.id)) ++= allConstraints
    } yield ()).transactionally)
  }

  /**
   * Delete [[modules.core.model.Constraint Constraints]] of a [[modules.subject.model.Collection Collection]] associated
   * [[modules.core.model.EntityType EntityType]].
   * <p> This operation deletes all [[modules.core.model.Property Properties]] associated to HasProperty Constraints
   * (here represented by propertyConstraints seq)
   * <p> The otherConstraints must contain Constraints which are not of HasProperty type.
   * <p> <strong> If HasProperty Constraints are not deleted separately (by putting them in otherConstraints or just
   * calling [[modules.core.repository.ConstraintRepository#deleteConstraint]]) the type system of the database will
   * be damaged and the system becomes unusable!</strong>
   *
   * @param typeId              id of the parent EntityType
   * @param propertyConstraints Constraints to delete of the HasProperty type
   * @param otherConstraints    Constraints to delete NOT of the HasProperty type
   * @return Future[Unit]
   */
  def deleteConstraints(typeId: Long, propertyConstraints: Seq[Constraint], otherConstraints: Seq[Constraint]): Future[Unit] = {

    val deletedPropertyKeys = propertyConstraints.map(_.v1) toSet
    val deletedConstraintIds = propertyConstraints ++ otherConstraints map (_.id) toSet

    val entitiesIDsWithType = collections.filter(_.typeId === typeId).map(_.entityId)

    db.run((for {
      _ <- properties.filter(_.parentId in entitiesIDsWithType).filter(_.key.inSet(deletedPropertyKeys)).delete
      _ <- constraints.filter(_.id.inSet(deletedConstraintIds)).delete
    } yield ()).transactionally)
  }

}
