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
import modules.core.model.{Constraint, FlimeyEntity, Property, Viewer}
import modules.core.repository._
import modules.subject.model._
import modules.user.model.ViewerCombinator
import modules.user.repository.GroupTable
import play.api.db.slick.{DatabaseConfigProvider, HasDatabaseConfigProvider}
import play.db.NamedDatabase
import slick.jdbc.JdbcProfile
import slick.jdbc.PostgresProfile.api._
import slick.lifted.TableQuery

import scala.concurrent.{ExecutionContext, Future}

/**
 * Repository to perform database operation on [[modules.subject.model.Frame Frame]] and associated entities.
 *
 * @param dbConfigProvider injected db configuration
 * @param executionContext implicit ExecutionContext
 */
class FrameRepository @Inject()(@NamedDatabase("flimey_data") protected val dbConfigProvider: DatabaseConfigProvider)(
  implicit executionContext: ExecutionContext) extends HasDatabaseConfigProvider[JdbcProfile] {

  val frames = TableQuery[FrameTable]
  val subjects = TableQuery[SubjectTable]
  val entities = TableQuery[FlimeyEntityTable]
  val entityTypes = TableQuery[TypeTable]
  val typeVersions = TableQuery[TypeVersionTable]
  val constraints = TableQuery[ConstraintTable]
  val properties = TableQuery[PropertyTable]
  val viewers = TableQuery[ViewerTable]
  val groups = TableQuery[GroupTable]

  /**
   * Add a new [[modules.subject.model.Frame Frame]] with [[modules.core.model.Property Properties]] to the db.<br />
   * The Frame id and Property ids are set to 0 to enable auto increment.
   * <p> Only valid Frame configurations should be added to the repository.
   *
   * @param frame    new Frame entity
   * @param newProperties Properties of the Frame
   * @param newViewers    [[modules.core.model.Viewer Viewers]] of the Frame.
   * @return Future[Unit]
   */
  def add(frame: Frame, newProperties: Seq[Property], newViewers: Seq[Viewer]): Future[Long] = {
    db.run((for {
      entityId <- (entities returning entities.map(_.id)) += FlimeyEntity(0)
      frameId <- (frames returning frames.map(_.id)) += Frame(0, entityId, frame.typeVersionId, frame.status, frame.created)
      _ <- properties ++= newProperties.map(p => Property(0, p.key, p.value, entityId))
      _ <- viewers ++= newViewers.map(v => Viewer(0, entityId, v.viewerId, v.role))
    } yield frameId).transactionally)
  }

  /**
   * Delete a [[modules.subject.model.Frame Frame]] and all associated data from the db.
   * <p> Deletes also all [[modules.subject.model.Subject Subjects]]!
   *
   * @param frame the Frame to delete
   * @return Future[Unit]
   */
  def delete(frame: Frame): Future[Unit] = {
    val subjectEntityIDsToDelete = subjects.filter(_.frameId === frame.id).map(_.entityId)
    db.run((for {
      _ <- properties.filter(_.parentId === frame.entityId).delete
      _ <- viewers.filter(_.targetId === frame.entityId).delete
      _ <- properties.filter(_.parentId in subjectEntityIDsToDelete).delete
      _ <- subjects.filter(_.entityId in subjectEntityIDsToDelete).delete
      _ <- entities.filter(_.id in subjectEntityIDsToDelete).delete
      //TODO delete attachments here
      _ <- frames.filter(_.id === frame.id).delete
      _ <- entities.filter(_.id === frame.entityId).delete
    } yield ()).transactionally)
  }

  /**
   * Update the state attribute of a [[modules.subject.model.Frame Frame]]
   *
   * @param frameId id of the Frame to update
   * @param newState     new state string state value
   * @return Future[Int]
   */
  def updateState(frameId: Long, newState: SubjectState.State): Future[Int] = {
    db.run(frames.filter(_.id === frameId).map(_.status).update(newState.toString))
  }

  /**
   * Get all [[modules.subject.model.FrameHeader FrameHeaders]] which are accessible by the given
   * [[modules.user.model.Group Groups]].
   * <p> Note that this operation is not limited so that large amounts of data could be returned.
   *
   * @param groupIds groups which must have access to the selected Frames
   * @return Future Seq[FrameHeader]
   */
  def getFrameHeaders(groupIds: Set[Long]): Future[Seq[FrameHeader]] = {

    //build sub-query to get all ids of frames which can be accessed by the given groups
    val subQuery = (for {
      (c, s) <- frames join viewers.filter(_.viewerId.inSet(groupIds)) on (_.entityId === _.targetId)
    } yield (c, s)).groupBy(_._1.id).map(_._1)

    //run the sub query to get all frames which are not archived and can be accessed.
    //those frames are used in separate queries to aggregate all necessary associated data.
    db.run(frames.filter(_.id in subQuery).filter(_.status =!= SubjectState.ARCHIVED.toString).sortBy(_.id.asc).result) flatMap( accessableFrames => {

      val accessableFrameIds = accessableFrames.map(_.id)
      val accessableFrameEntityIds = accessableFrames.map(_.entityId)
      val accessableFrameTypeVersionIds = accessableFrames.map(_.typeVersionId)

      val propertyQuery = properties.filter(_.parentId inSet accessableFrameEntityIds)
      val viewerQuery = viewers.filter(_.targetId inSet accessableFrameEntityIds) join groups on (_.viewerId === _.id)
      val typeQuery = typeVersions.filter(_.id inSet accessableFrameTypeVersionIds) join entityTypes on (_.typeId === _.id)
      val subjectQuery = subjects.filter(_.frameId inSet accessableFrameIds) join properties on (_.entityId === _.parentId)

      for {
        propertyResult <- db.run(propertyQuery.result)
        viewerResult <- db.run(viewerQuery.result)
        subjectResult <- db.run(subjectQuery.result)
        typeResult <- db.run(typeQuery.result)
      } yield {
        accessableFrames.map(frame => {

          val properties = propertyResult.filter(_.parentId == frame.entityId).sortBy(_.id)
          val viewerRelations = viewerResult.filter(_._1.targetId == frame.entityId).map(_.swap)
          val subjectsData = subjectResult.filter(_._1.frameId == frame.id).groupBy(_._1).mapValues(values => values.map(_._2))
          val entityType = typeResult.find(_._1.id == frame.typeVersionId).get._2

          val subjects: Seq[SubjectHeader] = parseSubjects(subjectsData)

          FrameHeader(frame, subjects, properties, ViewerCombinator.fromRelations(viewerRelations), Some(entityType))

        }).sortBy(_.frame.id)
      }
    })
  }

  /**
   * TODO add doc
   * @param subjectsData
   * @return
   */
  private def parseSubjects(subjectsData: Map[Subject, Seq[Property]]): Seq[SubjectHeader] = {
    subjectsData.keys.map(subject => {
      val properties = subjectsData(subject).sortBy(_.id)
      SubjectHeader(subject, properties, None)
    }).toSeq.sortBy(_.subject.id)
  }

  /**
   * TODO add doc
   * @param nameQuery
   * @param groupIds
   * @return
   */
  def findArchivedFrames(nameQuery: String, groupIds: Set[Long]): Future[Seq[ArchivedFrame]] = {
    val accessQuery = (for {
      (c, s) <- frames join viewers.filter(_.viewerId.inSet(groupIds)) on (_.entityId === _.targetId)
    } yield (c, s)).groupBy(_._1.id).map(_._1)

    val resultIDQuery = (frames.filter(_.id in accessQuery).filter(_.status === SubjectState.ARCHIVED.toString) join
      properties.filter(_.key === "Name").filter(_.value like s"%$nameQuery%") on (_.entityId === _.parentId)).map(_._1.id).take(256)

    val resultQuery = frames.filter(_.id in resultIDQuery) join properties on(_.entityId === _.parentId)

    db.run(resultQuery.result).map(res => {
      val framesWithProperties = res.groupBy(_._1).mapValues(values => values.map(_._2))
      framesWithProperties.keys.toSeq.sortBy(_.id).map(frame => {
        val properties = framesWithProperties(frame)
        ArchivedFrame(frame, properties.sortBy(_.id))
      })
    })
  }

  /**
   * Get a single [[modules.subject.model.ExtendedFrame ExtendedFrame]] by its id. The given
   * [[modules.user.model.Group Group]] ids must give access rights to the [[modules.subject.model.Frame Frame]].
   * <p> If the id does not exists or there are no access rights, nothing is returned.
   *
   * @param frameId id of the Frame to get
   * @param groupIds     Group ids of which at least one must have access to the Frame
   * @return Future Option[ExtendedFrame]
   */
  def getFrame(frameId: Long, groupIds: Set[Long]): Future[Option[ExtendedFrame]] = {

    val accessQuery = (for {
      (c, s) <- frames.filter(_.id === frameId) join viewers.filter(_.viewerId.inSet(groupIds)) on (_.entityId === _.targetId)
    } yield (c, s)).groupBy(_._1.id).map(_._1)

    val frameQuery = frames.filter(_.id in accessQuery)

    val propertyQuery = frameQuery joinLeft properties on (_.entityId === _.parentId)
    val viewerQuery = frameQuery join (groups join viewers on (_.id === _.viewerId)) on (_.entityId === _._2.targetId)
    val typeQuery = frameQuery join typeVersions on (_.typeVersionId === _.id) join entityTypes on (_._2.typeId === _.id)

    //fetch all subjects with properties
    val subjectQuery = frameQuery join (subjects join properties on (_.entityId === _.parentId)) on (_.id === _._1.frameId)
    val subjectTypeQuery = frameQuery join subjects on (_.id === _.frameId) join
      typeVersions on (_._2.typeVersionId === _.id) join entityTypes on (_._2.typeId === _.id)

    for {
      propertyResult <- db.run(propertyQuery.result)
      viewerResult <- db.run(viewerQuery.result)
      subjectResult <- db.run(subjectQuery.result)
      typeResult <- db.run(typeQuery.result)
      subjectTypeResult <- db.run(subjectTypeQuery.result)
    } yield {
      val frameWithProperties = propertyResult.groupBy(_._1).mapValues(values => values.map(_._2)).headOption
      val frameWithViewers = viewerResult.groupBy(_._1).mapValues(values => values.map(_._2)).headOption
      val frameWithSubjectData = subjectResult.groupBy(_._1).mapValues(values =>
        values.map(_._2).groupBy(_._1).mapValues(cValues => cValues.map(_._2)))
      val frameWithType = typeResult.groupBy(_._1._1).mapValues(_.head._2)
      val subjectsWithTypes = subjectTypeResult.groupBy(_._1._1._2).mapValues(_.head._2)

      if (frameWithViewers.isEmpty) {
        None
      } else {
        val frame = frameWithProperties.get._1
        var subjects: Seq[SubjectHeader] = Seq()
        val entityType = frameWithType(frame)
        if (frameWithSubjectData.contains(frame)) subjects = parseSubjects(frameWithSubjectData(frame))
        subjects = subjects.map(value => SubjectHeader(value.subject, value.properties, subjectsWithTypes.get(value.subject)))
        Some(ExtendedFrame(
          frame,
          subjects,
          frameWithProperties.get._2.filter(_.isDefined).map(_.get),
          Seq(), //TODO Attachments
          ViewerCombinator.fromRelations(frameWithViewers.get._2),
          entityType))
      }
    }
  }

  /**
   * Get a single [[modules.subject.model.FrameHeader FrameHeader]] WITHOUT Subject data by its id. The given
   * [[modules.user.model.Group Group]] ids must give access rights to the [[modules.subject.model.Frame Frame]].
   * <p> If the id does not exists or there are no access rights, nothing is returned.
   *
   * @param frameId id of the Frame to get
   * @param groupIds     Group ids of which at least one must have access to the Frame
   * @return Future Option[FrameHeader]
   */
  def getSlimFrame(frameId: Long, groupIds: Set[Long]): Future[Option[FrameHeader]] = {

    val accessQuery = (for {
      (c, s) <- frames.filter(_.id === frameId) join viewers.filter(_.viewerId.inSet(groupIds)) on (_.entityId === _.targetId)
    } yield (c, s)).groupBy(_._1.id).map(_._1)

    val frameQuery = frames.filter(_.id in accessQuery)

    val propertyQuery = frameQuery joinLeft properties on (_.entityId === _.parentId)
    val viewerQuery = frameQuery join (groups join viewers on (_.id === _.viewerId)) on (_.entityId === _._2.targetId)

    for {
      propertyResult <- db.run(propertyQuery.result)
      viewerResult <- db.run(viewerQuery.result)
    } yield {
      val frameWithProperties = propertyResult.groupBy(_._1).mapValues(values => values.map(_._2)).headOption
      val frameWithViewers = viewerResult.groupBy(_._1).mapValues(values => values.map(_._2)).headOption

      if (frameWithProperties.isEmpty) {
        None
      } else {
        Some(FrameHeader(
          frameWithProperties.get._1,
          Seq(), //No Subjects here - that's the slim part ;)
          frameWithProperties.get._2.filter(_.isDefined).map(_.get),
          ViewerCombinator.fromRelations(frameWithViewers.get._2),
          None))
      }
    }
  }

  /**
   * Delete a [[modules.core.model.EntityType EntityType]] of a [[modules.subject.model.Frame Frame]].
   * <p> To ensure integrity, this operation deletes:
   * <p> 1. all [[modules.core.model.Constraint Constraints]] of the type.
   * <p> 2. all [[modules.core.model.FlimeyEntity Entities (Frames)]] which use this type...
   * <p> 3. ... with all their [[modules.core.model.Property Properties]].
   * <p> 4. all to Entities of this type associated [[modules.core.model.Viewer Viewers]].
   *
   * @param typeId of the EntityType (FrameType) to delete
   * @return Future[Unit]
   */
  def deleteFrameType(typeId: Long): Future[Unit] = {
    val typeVersionsToDeleteIds = typeVersions.filter(_.typeId === typeId).map(_.id)
    val framesOfTypeEntityIds = frames.filter(_.typeVersionId in typeVersionsToDeleteIds).map(_.entityId)
    db.run((for {
      _ <- properties.filter(_.parentId in framesOfTypeEntityIds).delete
      _ <- viewers.filter(_.targetId in framesOfTypeEntityIds).delete
      _ <- frames.filter(_.entityId in framesOfTypeEntityIds).delete
      _ <- entities.filter(_.id in framesOfTypeEntityIds).delete
      _ <- constraints.filter(_.typeVersionId in typeVersionsToDeleteIds).delete
      _ <- typeVersions.filter(_.id in typeVersionsToDeleteIds).delete
      _ <- entityTypes.filter(_.id === typeId).delete
    } yield ()).transactionally)
  }

  /**
   * Delete a [[modules.core.model.TypeVersion TypeVersion]] of a [[modules.subject.model.Frame Frame]].
   * <p> To ensure integrity, this operation deletes:
   * <p> 1. all [[modules.core.model.Constraint Constraints]] of the type.
   * <p> 2. all [[modules.core.model.FlimeyEntity Entities (Frames)]] which use this type...
   * <p> 3. ... with all their [[modules.core.model.Property Properties]].
   * <p> 4. all to Entities of this type associated [[modules.core.model.Viewer Viewers]].
   *
   * @param typeVersionId id of the [[modules.core.model.TypeVersion TypeVersion]]
   * @return Future[Unit]
   */
  def deleteFrameTypeVersion(typeVersionId: Long): Future[Unit] = {
    val framesToDeleteEntityIds = frames.filter(_.typeVersionId === typeVersionId).map(_.entityId)
    db.run((for {
      _ <- properties.filter(_.parentId in framesToDeleteEntityIds).delete
      _ <- viewers.filter(_.targetId in framesToDeleteEntityIds).delete
      _ <- frames.filter(_.entityId in framesToDeleteEntityIds).delete
      _ <- entities.filter(_.id in framesToDeleteEntityIds).delete
      _ <- constraints.filter(_.typeVersionId === typeVersionId).delete
      _ <- typeVersions.filter(_.id === typeVersionId).delete
    } yield ()).transactionally)
  }

  /**
   * Add new [[modules.core.model.Constraint Constraints]] to a [[modules.core.model.TypeVersion TypeVersion]] of
   * the [[modules.subject.model.Frame Frame]] subtype.
   * <p> The id of all new Constraints must be set to 0 to enable auto increment.
   * <p> This method makes a difference between new propertyConstraints (Constraints of the HasProperty type) and other
   * Constraints.
   * <p> <strong>If you add new HasProperty Constraints the wrong way (via otherConstraints or just
   * [[modules.core.repository.ConstraintRepository#addConstraint]]) will lead to loosing the integrity of the type
   * system. </strong>
   *
   * @param typeVersionId       id of the TypeVersion (of a Frame) to add the new Constraints to.
   * @param propertyConstraints new Constraints of HasProperty type
   * @param otherConstraints    new Constraints NOT of HasProperty type
   * @return Future[Unit]
   */
  def addConstraints(typeVersionId: Long, propertyConstraints: Seq[Constraint], otherConstraints: Seq[Constraint]): Future[Unit] = {

    val allConstraints = (propertyConstraints ++ otherConstraints) map (c => Constraint(c.id, c.c, c.v1, c.v2, c.byPlugin, typeVersionId))

    db.run((for {
      entityIDsWithType <- frames.filter(_.typeVersionId === typeVersionId).map(_.entityId).result
      _ <- DBIO.sequence(propertyConstraints.map(propertyConstraint => {
        properties ++= entityIDsWithType.map(entityId => Property(0, propertyConstraint.v1, "", entityId))
      }))
      _ <- (constraints returning constraints.map(_.id)) ++= allConstraints
    } yield ()).transactionally)
  }

  /**
   * Delete [[modules.core.model.Constraint Constraints]] of a [[modules.subject.model.Frame Frame]] associated
   * [[modules.core.model.EntityType EntityType]].
   * <p> This operation deletes all [[modules.core.model.Property Properties]] associated to HasProperty Constraints
   * (here represented by propertyConstraints seq)
   * <p> The otherConstraints must contain Constraints which are not of HasProperty type.
   * <p> <strong> If HasProperty Constraints are not deleted separately (by putting them in otherConstraints or just
   * calling [[modules.core.repository.ConstraintRepository#deleteConstraint]]) the type system of the database will
   * be damaged and the system becomes unusable!</strong>
   *
   * @param typeVersionId       id of the parent [[modules.core.model.TypeVersion TypeVersion]]
   * @param propertyConstraints Constraints to delete of the HasProperty type
   * @param otherConstraints    Constraints to delete NOT of the HasProperty type
   * @return Future[Unit]
   */
  def deleteConstraints(typeVersionId: Long, propertyConstraints: Seq[Constraint], otherConstraints: Seq[Constraint]): Future[Unit] = {

    val deletedPropertyKeys = propertyConstraints.map(_.v1) toSet
    val deletedConstraintIds = propertyConstraints ++ otherConstraints map (_.id) toSet

    val entitiesIDsWithType = frames.filter(_.typeVersionId === typeVersionId).map(_.entityId)

    db.run((for {
      _ <- properties.filter(_.parentId in entitiesIDsWithType).filter(_.key.inSet(deletedPropertyKeys)).delete
      _ <- constraints.filter(_.id.inSet(deletedConstraintIds)).delete
    } yield ()).transactionally)
  }

}