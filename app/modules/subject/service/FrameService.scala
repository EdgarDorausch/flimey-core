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
import modules.core.model.{Constraint, ExtendedEntityType}
import modules.core.repository.{FlimeyEntityRepository, TypeRepository}
import modules.core.service.EntityTypeService
import modules.news.model.NewsType
import modules.news.service.NewsService
import modules.subject.model._
import modules.subject.repository.FrameRepository
import modules.user.model.GroupStats
import modules.user.service.GroupService
import modules.user.util.ViewerAssertion

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

/**
 * The service class to provide safe functionality to work with Frames.
 * <p> Normally, this class is used with dependency injection in controller classes or as helper in other services.
 *
 * @param typeRepository         injected [[modules.core.repository.TypeRepository TypeRepository]]
 * @param frameRepository   injected [[modules.subject.repository.FrameRepository FrameRepository]]
 * @param entityRepository       injected [[modules.core.repository.FlimeyEntityRepository FlimeyEntityRepository]]
 * @param modelFrameService injected [[modules.subject.service.ModelFrameService ModelFrameService]]
 * @param groupService           injected [[modules.user.service.GroupService GroupService]]
 * @param newsService            injected [[modules.news.service.NewsService NewsService]]
 */
class FrameService @Inject()(typeRepository: TypeRepository,
                                  frameRepository: FrameRepository,
                                  entityRepository: FlimeyEntityRepository,
                                  modelFrameService: ModelFrameService,
                                  entityTypeService: EntityTypeService,
                                  groupService: GroupService,
                                  newsService: NewsService) {

  /**
   * Add a new [[modules.subject.model.Frame Frame]].
   * <p> A new Frame will always be created using the newest [[modules.core.model.TypeVersion TypeVersion]] available
   * for the specified [[modules.core.model.EntityType EntityType]].
   * <p> Invalid and duplicate names in maintainers, editors and viewers is filtered out and does not lead to exceptions.
   * <p> <strong> Note: a User (defined by his ticket) can create Frames he is unable to access himself (by assigning
   * other [[modules.user.model.Group Groups]]</strong>
   * <p> Fails without WORKER rights.
   * <p> This is a safe implementation and can be used by controller classes.
   *
   * @param typeId       id of the Frame [[modules.core.model.EntityType]]
   * @param propertyData of the new Frame (must complete the Frame EntityType model)
   * @param maintainers  names of Groups to serve as maintainers
   * @param editors      names of Groups to serve as editors
   * @param viewers      names of Groups to serve as viewers
   * @param ticket       implicit authentication ticket
   * @return Future[Unit]
   */
  def addFrame(typeId: Long, propertyData: Seq[String], maintainers: Seq[String], editors: Seq[String],
                    viewers: Seq[String])(implicit ticket: Ticket): Future[Unit] = {
    try {
      RoleAssertion.assertWorker
      modelFrameService.getLatestExtendedType(typeId) flatMap (extendedEntityType => {
        if (!extendedEntityType.entityType.active) throw new Exception("The selected Frame Type is not active")
        val properties = FrameLogic.derivePropertiesFromRawData(extendedEntityType.constraints, propertyData)
        val configurationStatus = FrameLogic.isModelConfiguration(extendedEntityType.constraints, properties)
        if (!configurationStatus.valid) configurationStatus.throwError
        groupService.getAllGroups flatMap (allGroups => {
          val aViewers = FrameLogic.deriveViewersFromData(maintainers :+ GroupStats.SYSTEM_GROUP, editors, viewers, allGroups)
          for {
            frameId <- frameRepository.add(Frame(0, 0, extendedEntityType.version.id, SubjectState.CREATED, Timestamp.from(Instant.now())), properties, aViewers)
            _ <- newsService.addFrameEvent(frameId, NewsType.CREATED, aViewers.map(_.viewerId).toSet)
          } yield ()
        })
      })
    } catch {
      case e: Throwable => Future.failed(e)
    }
  }

  /**
   * Update a [[modules.subject.model.Frame Frame]] with its [[modules.core.model.Property Properties]]
   * and [[modules.core.model.Viewer Viewers]].
   * <p> All Properties (if updated or not) must be passed, else the configuration can not be verified.
   * <p> All Viewers (old AND new ones) must be passed as string. Old viewers that are not passed will be deleted.
   * Invalid and duplicate Viewer names (Group names) are filtered out. Only the highest role is applied per Viewer.
   * The SYSTEM Group can not be removed as MAINTAINER.
   * <p> Fails without WORKER rights.
   * <p> If Properties are changed, EDITOR rights are required.
   * <p> If Viewers are changed, MAINTAINER rights are required.
   * <p> This is a safe implementation and can be used by controller classes.
   *
   * @param frameId       id of the Frame to update
   * @param propertyUpdateData all Properties of the changed Frame (can contain updated values)
   * @param maintainers        all (old and new) Group names of Viewers with role MAINTAINER
   * @param editors            all (old and new) Group names of Viewers with role EDITOR
   * @param viewers            all (old and new) Group names of Viewers with role VIEWER
   * @param ticket             implicit authentication ticket
   * @return Future[Unit]
   */
  def updateFrame(frameId: Long, propertyUpdateData: Seq[String], maintainers: Seq[String], editors: Seq[String],
                       viewers: Seq[String])(implicit ticket: Ticket): Future[Unit] = {
    try {
      RoleAssertion.assertWorker
      getSlimFrame(frameId) flatMap (frameHeader => {

        //Check if the User can edit this Frame
        ViewerAssertion.assertEdit(frameHeader.viewers)

        if(frameHeader.frame.status == SubjectState.ARCHIVED) throw new Exception("This element is already archived")

        //Parse updated properties and verify the configuration
        val properties = frameHeader.properties
        val oldConfig = properties
        val newConfig = FrameLogic.mapConfigurations(oldConfig, propertyUpdateData)

        //check if the EntityType of the Frame is active (else it can not be edited)
        modelFrameService.getExtendedType(frameHeader.frame.typeVersionId) flatMap (extendedEntityType => {
          if (!extendedEntityType.entityType.active) throw new Exception("The selected Frame Type is not active")
          val configurationStatus = FrameLogic.isModelConfiguration(extendedEntityType.constraints, newConfig)
          if (!configurationStatus.valid) configurationStatus.throwError

          groupService.getAllGroups flatMap (groups => {

            val (viewersToDelete, viewersToInsert) = FrameLogic.getViewerChanges(
              maintainers.toSet + GroupStats.SYSTEM_GROUP,
              editors.toSet,
              viewers.toSet,
              frameHeader.viewers,
              groups,
              frameHeader.frame.entityId)

            if (viewersToDelete.nonEmpty || viewersToInsert.nonEmpty) {
              ViewerAssertion.assertMaintain(frameHeader.viewers)
            }
            entityRepository.update(newConfig, viewersToDelete, viewersToInsert)
          })
        })
      })
    } catch {
      case e: Throwable => Future.failed(e)
    }
  }

  /**
   * Update the [[modules.subject.model.SubjectState State]] of a [[modules.subject.model.Frame Frame]].
   * <p> Fails without WORKER rights.
   * <p> The requesting User must be EDITOR.
   * <p> This is a safe implementation and can be used by controller classes.
   *
   * @param frameId id of the Frame to update
   * @param newState     state string value
   * @param ticket       implicit authentication ticket
   * @return Future[Unit]
   */
  def updateState(frameId: Long, newState: String)(implicit ticket: Ticket): Future[Int] = {
    try {
      RoleAssertion.assertWorker
      getFrame(frameId) flatMap (frameData => {
        val (frame, _) = frameData
        //Check if the User can edit this Frame
        ViewerAssertion.assertEdit(frame.viewers)
        val state = FrameLogic.parseState(newState)

        val updateStatus = FrameLogic.isValidStateTransition(frame.frame.status, state)
        if (!updateStatus.valid) updateStatus.throwError

        if(state == SubjectState.ARCHIVED){
          ViewerAssertion.assertMaintain(frame.viewers)
          //check if all children are closed with success or failure
          val readyToArchive = FrameLogic.isReadyToArchive(frame.subjects)
          if(!readyToArchive.valid) readyToArchive.throwError
        }

        for {
          res <- frameRepository.updateState(frameId, state)
          _ <- newsService.addFrameEvent(frameId, NewsType.STATE_CHANGE,
            frame.viewers.getAllViewingGroups.map(_.id))
        } yield res
      })
    } catch {
      case e: Throwable => Future.failed(e)
    }
  }

  /**
   * Get an [[modules.subject.model.ExtendedFrame ExtendedFrame]] together with its
   * [[modules.core.model.ExtendedEntityType ExtendedEntityType]] by its id.
   * <p> A User (given by his ticket) can only request Frames he has access rights to.
   * <p> Fails without WORKER rights.
   * <p> This is a safe implementation and can be used by controller classes.
   *
   * @param frameId id of the Frame to fetch
   * @param ticket       implicit authentication ticket
   * @return Future[(ExtendedFrame, ExtendedEntityType)]
   */
  def getFrame(frameId: Long)(implicit ticket: Ticket): Future[(ExtendedFrame, ExtendedEntityType)] = {
    try {
      RoleAssertion.assertWorker
      val accessedGroupIds = ticket.accessRights.getAllViewingGroupIds
      frameRepository.getFrame(frameId, accessedGroupIds) flatMap (frameData => {
        if (frameData.isEmpty) throw new Exception("Frame does not exist or missing rights")
        val extendedFrame = frameData.get
        modelFrameService.getExtendedType(extendedFrame.frame.typeVersionId) map (extendedEntityType => {
          (extendedFrame, extendedEntityType)
        })
      })
    } catch {
      case e: Throwable => Future.failed(e)
    }
  }

  /**
   * Get a [[modules.subject.model.FrameHeader FrameHeader]] WITHOUT its children
   * [[modules.subject.model.Subject Subject]] data but together with its
   * [[modules.core.model.Property Properties]] and [[modules.core.model.Viewer Viewers]] by id.
   * <p> A User (given by his ticket) can only request Frames he has access rights to.
   * <p> Fails without WORKER rights.
   * <p> This is a safe implementation and can be used by controller classes.
   *
   * @param frameId id of the Frame to fetch
   * @param ticket       implicit authentication ticket
   * @return Future[FrameHeader]
   */
  def getSlimFrame(frameId: Long)(implicit ticket: Ticket): Future[FrameHeader] = {
    try {
      RoleAssertion.assertWorker
      val accessedGroupIds = ticket.accessRights.getAllViewingGroupIds
      frameRepository.getSlimFrame(frameId, accessedGroupIds) map (frameOption => {
        if (frameOption.isEmpty) throw new Exception("Frame does not exist or missing rights")
        frameOption.get
      })
    } catch {
      case e: Throwable => Future.failed(e)
    }
  }

  /**
   * TODO add doc
   * @param nameQuery
   * @param ticket
   * @return
   */
  def findArchivedFrame(nameQuery: String)(implicit ticket: Ticket): Future[Seq[ArchivedFrame]] = {
    try {
      RoleAssertion.assertWorker
      val accessedGroupIds = ticket.accessRights.getAllViewingGroupIds
      frameRepository.findArchivedFrames(nameQuery, accessedGroupIds)
    } catch {
      case e: Throwable => Future.failed(e)
    }
  }

  /**
   * Get all [[modules.subject.model.FrameHeader FrameHeaders]]
   * <p> Only Frame data the given User (by ticket) can access is returned.
   * <p> Fails without WORKER rights
   * <p> This is a safe implementation and can be used by controller classes.
   *
   * @param groupSelector [[modules.user.model.Group Groups]] which must contain the returned Frame data
   *                      (must be partition of ticket Groups)
   * @param ticket        implicit authentication ticket
   * @return Future Seq[FrameHeader]
   */
  def getFrameHeaders(typeSelector: Option[String] = None, groupSelector: Option[String] = None)(implicit ticket: Ticket):
  Future[Seq[FrameHeader]] = {
    try {
      RoleAssertion.assertWorker
      var accessedGroupIds = ticket.accessRights.getAllViewingGroupIds
      if (groupSelector.isDefined) {
        val selectedGroups = FrameLogic.splitNumericList(groupSelector.get)
        accessedGroupIds = accessedGroupIds.filter(!selectedGroups.contains(_))
      }
      //FIXME handle type selector (best would be a "exclude type" selector)
      frameRepository.getFrameHeaders(accessedGroupIds)
    } catch {
      case e: Throwable => Future.failed(e)
    }
  }

  /**
   * Get all [[modules.subject.model.FrameHeader FrameHeaders]] and all
   * [[modules.core.model.ExtendedEntityType ExtendedEntityTypes]] which define them.
   * <p> This method calls [[modules.subject.service.FrameService#getFrameHeaders]] (see there for more information)
   * <p> Fails without WORKER rights.
   * <p> This is a safe implementation and can be used by controller classes.
   *
   * @param groupSelector [[modules.user.model.Group Groups]] which must contain the returned Frame
   *                      (must be partition of ticket Groups)
   * @param ticket        implicit authentication ticket
   * @return Future[FrameTypeComplex]
   */
  def getFrameComplex(typeSelector: Option[String] = None, groupSelector: Option[String] = None)(implicit ticket: Ticket):
  Future[FrameTypeComplex] = {
    try {
      for {
        frameHeaders <- getFrameHeaders(typeSelector, groupSelector)
        entityTypes <- entityTypeService.getAllTypes(Some(FrameConstraintSpec.FRAME))
      } yield {
        FrameTypeComplex(frameHeaders, entityTypes)
      }
    } catch {
      case e: Throwable => Future.failed(e)
    }
  }

  /**
   * Delete a [[modules.subject.model.Frame Frame]].
   * <p> <strong> This will also delete all child [[modules.subject.model.Subject Subjects]]!</strong>
   * <p> This is a safe implementation and can be used by controller classes.
   * <p> Fails without MAINTAINER rights
   *
   * @param id     of the Frame
   * @param ticket implicit authentication ticket
   * @return Future[Unit]
   */
  def deleteFrame(id: Long)(implicit ticket: Ticket): Future[Unit] = {
    try {
      RoleAssertion.assertWorker
      getSlimFrame(id) flatMap (frameData => {
        ViewerAssertion.assertMaintain(frameData.viewers)
        for {
          _ <- frameRepository.delete(frameData.frame)
          _ <- newsService.addFrameEvent(id, NewsType.DELETED,
            frameData.viewers.getAllViewingGroups.map(_.id))
        } yield ()
      })
    } catch {
      case e: Throwable => Future.failed(e)
    }
  }

  /**
   * Forwards to same method of [[modules.subject.service.FrameLogic FrameLogic]].
   * <p> This is a safe implementation and can be used by controller classes.
   * <p> Fails without WORKER rights
   *
   * @param constraints model of an [[modules.core.model.TypeVersion TypeVersion]]
   * @param ticket      implicit authentication ticket
   * @return Seq[(String, String)] (property key -> data type)
   */
  def getFramePropertyKeys(constraints: Seq[Constraint])(implicit ticket: Ticket): Seq[(String, String)] = {
    RoleAssertion.assertWorker
    FrameLogic.getPropertyKeys(constraints)
  }

  /**
   * Forwards to same method of [[modules.subject.service.FrameLogic FrameLogic]].
   * <p> This is a safe implementation and can be used by controller classes.
   * <p> Fails without WORKER rights
   *
   * @param constraints model of an FrameType
   * @param ticket      implicit authentication ticket
   * @return Map[String, String] (property key -> default value)
   */
  def getObligatoryPropertyKeys(constraints: Seq[Constraint])(implicit ticket: Ticket): Map[String, String] = {
    RoleAssertion.assertWorker
    FrameLogic.getObligatoryPropertyKeys(constraints)
  }

}