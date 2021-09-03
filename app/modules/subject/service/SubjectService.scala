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

import java.sql.Timestamp
import java.time.Instant

import com.google.inject.Inject
import modules.auth.model.Ticket
import modules.auth.util.RoleAssertion
import modules.core.model.Constraint
import modules.core.repository.FlimeyEntityRepository
import modules.news.model.NewsType
import modules.news.service.NewsService
import modules.subject.model._
import modules.subject.repository.SubjectRepository
import modules.user.util.ViewerAssertion

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

/**
 * The service class to provide safe functionality to work with [[modules.subject.model.Subject Subjects]].
 * <p> Normally, this class is used with dependency injection in controller classes or as helper in other services.
 *
 * @param subjectRepository   injected [[modules.subject.repository.SubjectRepository SubjectRepository]]
 * @param entityRepository        injected [[modules.core.repository.FlimeyEntityRepository FlimeyEntityRepository]]
 * @param collectionService       injected [[modules.subject.service.CollectionService CollectionService]]
 * @param modelSubjectService injected [[modules.subject.service.ModelSubjectService ModelSubjectService]]
 * @param modelCollectionService  injected [[modules.subject.service.ModelCollectionService ModelCollectionService]]
 * @param newsService             injected [[modules.news.service.NewsService]]
 */
class SubjectService @Inject()(subjectRepository: SubjectRepository,
                                   collectionService: CollectionService,
                                   modelSubjectService: ModelSubjectService,
                                   modelCollectionService: ModelCollectionService,
                                   entityRepository: FlimeyEntityRepository,
                                   newsService: NewsService) {

  /**
   * Add a new [[modules.subject.model.Subject Subject]].
   * <p> To create the Subject, the newest available [[modules.core.model.TypeVersion TypeVersion]] of the selected
   * [[modules.core.model.EntityType EntityType]].
   * <p> Fails without WORKER rights.
   * <p> Requires EDITOR rights in the parent Collection.
   * <p> This is a safe implementation and can be used by controller classes.
   *
   * @param collectionId id of the parent [[modules.subject.model.Collection Collection]]
   * @param typeId       id of the Subject [[modules.core.model.EntityType]]
   * @param propertyData of the new Subject (must complete the Subject TypeVersion model)
   * @param ticket       implicit authentication ticket
   * @return Future[Unit]
   */
  def addSubject(collectionId: Long, typeId: Long, propertyData: Seq[String])(implicit ticket: Ticket): Future[Unit] = {
    try {
      RoleAssertion.assertWorker
      collectionService.getSlimCollection(collectionId) flatMap (collectionHeader => {
        ViewerAssertion.assertEdit(collectionHeader.viewers)
        for {
          collectionTypeData <- modelCollectionService.getExtendedType(collectionHeader.collection.typeVersionId)
          subjectTypeData <- modelSubjectService.getLatestExtendedType(typeId)
        } yield {
          if (!collectionTypeData.entityType.active) throw new Exception("The selected Collection Type is not defined or active")
          if (!subjectTypeData.entityType.active) throw new Exception("The selected Subject Type is not defined or active")
          val extensionStatus = SubjectLogic.canBeChildOf(subjectTypeData.entityType.value, collectionTypeData.constraints)
          if (!extensionStatus.valid) extensionStatus.throwError

          val properties = SubjectLogic.derivePropertiesFromRawData(subjectTypeData.constraints, propertyData)
          val configurationStatus = SubjectLogic.isModelConfiguration(subjectTypeData.constraints, properties)
          if (!configurationStatus.valid) configurationStatus.throwError

          val newSubject = Subject(0, 0, collectionHeader.collection.id, subjectTypeData.version.id, SubjectState.CREATED, Timestamp.from(Instant.now()))
          for {
            subjectId <- subjectRepository.add(newSubject, properties)
            _ <- newsService.addSubjectEvent(collectionId, subjectId, NewsType.CREATED, collectionHeader.viewers.getAllViewingGroups.map(_.id))
          } yield ()

        }
      })
    } catch {
      case e: Throwable => Future.failed(e)
    }
  }

  /**
   * Update a [[modules.subject.model.Subject Subject]] with its [[modules.core.model.Property Properties]].
   * <p> All Properties (if updated or not) must be passed, else the configuration can not be verified.
   * <p> Fails without WORKER rights.
   * <p> If Properties are changed, EDITOR rights are required.
   * <p> This is a safe implementation and can be used by controller classes.
   *
   * @param subjectId      id of the Subject to update
   * @param propertyUpdateData all Properties of the changed Subject (can contain updated values)
   * @param ticket             implicit authentication ticket
   * @return Future[Unit]
   */
  def updateSubject(subjectId: Long, propertyUpdateData: Seq[String])(implicit ticket: Ticket): Future[Unit] = {
    try {
      RoleAssertion.assertWorker
      getSubject(subjectId) flatMap (extendedSubject => {

        //Check if the User can edit this Subject
        ViewerAssertion.assertEdit(extendedSubject.viewers)

        if(extendedSubject.subject.state == SubjectState.ARCHIVED) throw new Exception("This element is already archived")

        //Parse updated properties and verify the configuration
        val properties = extendedSubject.properties
        val oldConfig = properties
        val newConfig = SubjectLogic.mapConfigurations(oldConfig, propertyUpdateData)

        //check if the EntityType of the Subject is active (else it can not be edited)
        modelSubjectService.getExtendedType(extendedSubject.subject.typeVersionId) flatMap (extendedEntityType => {
          if (!extendedEntityType.entityType.active) throw new Exception("The selected Subject Type is not active")
          val configurationStatus = SubjectLogic.isModelConfiguration(extendedEntityType.constraints, newConfig)
          if (!configurationStatus.valid) configurationStatus.throwError

          entityRepository.update(newConfig, Set(), Set())
        })
      })
    } catch {
      case e: Throwable => Future.failed(e)
    }
  }

  /**
   * Update the [[modules.subject.model.SubjectState State]] of a [[modules.subject.model.Subject Subject]].
   * <p> Fails without WORKER rights.
   * <p> The requesting User must be EDITOR.
   * <p> This is a safe implementation and can be used by controller classes.
   *
   * @param subjectId id of the Subject to update
   * @param newState      state string value
   * @param ticket        implicit authentication ticket
   * @return Future[Unit]
   */
  def updateState(subjectId: Long, newState: String)(implicit ticket: Ticket): Future[Unit] = {
    try {
      RoleAssertion.assertWorker
      getSubject(subjectId) flatMap (extendedSubject => {
        //Check if the User can edit this Subject
        ViewerAssertion.assertEdit(extendedSubject.viewers)
        val state = CollectionLogic.parseState(newState)
        val updateStatus = SubjectLogic.isValidStateTransition(extendedSubject.subject.state, state)
        if (!updateStatus.valid) updateStatus.throwError
        for {
          _ <- subjectRepository.updateState(subjectId, state)
          _ <- newsService.addSubjectEvent(extendedSubject.subject.collectionId, subjectId, NewsType.STATE_CHANGE,
            extendedSubject.viewers.getAllViewingGroups.map(_.id))
        } yield ()
      })
    } catch {
      case e: Throwable => Future.failed(e)
    }
  }

  /**
   * Get a [[modules.subject.model.ExtendedSubject ExtendedSubject]] together with its
   * [[modules.core.model.Property Properties]] and [[modules.core.model.Viewer Viewers]] by id.
   * <p> A User (given by his ticket) can only request Subjects he has access rights to.
   * <p> Fails without WORKER rights.
   * <p> This is a safe implementation and can be used by controller classes.
   *
   * @param subjectId id of the Subject to fetch
   * @param ticket        implicit authentication ticket
   * @return Future[ExtendedSubject]
   */
  def getSubject(subjectId: Long)(implicit ticket: Ticket): Future[ExtendedSubject] = {
    try {
      RoleAssertion.assertWorker
      val accessedGroupIds = ticket.accessRights.getAllViewingGroupIds
      subjectRepository.getExtendedSubject(subjectId) map (extendedSubjectOption => {
        if (extendedSubjectOption.isEmpty) throw new Exception("No such a Subject found")
        val extendedSubject = extendedSubjectOption.get
        ViewerAssertion.assertView(extendedSubject.viewers)
        extendedSubject
      })
    } catch {
      case e: Throwable => Future.failed(e)
    }
  }

  /**
   * Delete a [[modules.subject.model.Subject Subject]].
   * <p> This is a safe implementation and can be used by controller classes.
   * <p> Fails without MAINTAINER rights
   *
   * @param id     of the Subject
   * @param ticket implicit authentication ticket
   * @return Future[Unit]
   */
  def deleteSubject(id: Long)(implicit ticket: Ticket): Future[Unit] = {
    try {
      RoleAssertion.assertWorker
      getSubject(id) flatMap (extendedSubject => {
        ViewerAssertion.assertMaintain(extendedSubject.viewers)
        for {
          _ <- subjectRepository.delete(extendedSubject.subject)
          _ <- newsService.addSubjectEvent(extendedSubject.subject.collectionId, id, NewsType.DELETED,
            extendedSubject.viewers.getAllViewingGroups.map(_.id))
        } yield ()
      })
    } catch {
      case e: Throwable => Future.failed(e)
    }
  }

  /**
   * Forwards to same method of [[modules.subject.service.SubjectLogic SubjectLogic]].
   * <p> This is a safe implementation and can be used by controller classes.
   * <p> Fails without WORKER rights
   *
   * @param constraints model of a [[modules.core.model.TypeVersion TypeVersion]]
   * @param ticket      implicit authentication ticket
   * @return Seq[(String, String)] (property key -> data type)
   */
  def getSubjectPropertyKeys(constraints: Seq[Constraint])(implicit ticket: Ticket): Seq[(String, String)] = {
    RoleAssertion.assertWorker
    SubjectLogic.getPropertyKeys(constraints)
  }

  /**
   * Forwards to same method of [[modules.subject.service.SubjectLogic SubjectLogic]].
   * <p> This is a safe implementation and can be used by controller classes.
   * <p> Fails without WORKER rights
   *
   * @param constraints model of a [[modules.core.model.TypeVersion TypeVersion]]
   * @param ticket      implicit authentication ticket
   * @return Map[String, String] (property key -> default value)
   */
  def getObligatoryPropertyKeys(constraints: Seq[Constraint])(implicit ticket: Ticket): Map[String, String] = {
    RoleAssertion.assertWorker
    SubjectLogic.getObligatoryPropertyKeys(constraints)
  }

}
