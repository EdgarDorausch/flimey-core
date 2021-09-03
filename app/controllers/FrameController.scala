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

package controllers

import javax.inject.{Inject, Singleton}
import middleware.{AuthenticatedRequest, Authentication, AuthenticationFilter}
import modules.auth.model.Ticket
import modules.core.formdata.{EntityForm, SelectValueForm}
import modules.subject.service.{FrameService, ModelFrameService}
import modules.user.service.GroupService
import play.api.Logging
import play.api.data.Form
import play.api.i18n.I18nSupport
import play.api.mvc._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

/**
 * Controller to provide all required endpoints to manage [[modules.subject.model.Frame Frames]]
 *
 * @param cc                     injected ControllerComponents (provides methods and implicits)
 * @param withAuthentication     injected [[middleware.AuthenticationFilter AuthenticationFilter]] to handle session verification
 * @param frameService      injected [[modules.subject.service.FrameService FrameService]]
 * @param modelFrameService injected [[modules.subject.service.ModelFrameService ModelFrameService]]
 * @param groupService           injected [[modules.user.service.GroupService GroupService]]
 */
@Singleton
class FrameController @Inject()(cc: ControllerComponents,
                                     withAuthentication: AuthenticationFilter,
                                     frameService: FrameService,
                                     modelFrameService: ModelFrameService,
                                     groupService: GroupService) extends
  AbstractController(cc) with I18nSupport with Logging with Authentication {

  /**
   * Endpoint to show the [[modules.subject.model.Frame Frame]] overview page.
   * <p> Depending on the flashed error, different configurations are shown:
   * <p> 1. There is an "recursive_error" (an error that will lead to recursive redirect), the redirect is stopped and
   * an empty frame page is returned
   * <p> 2. In all other cases the getFrames() is redirected, with or without not recursive error
   *
   * @return redirect to getFrames() or empty error overview page
   */
  def index: Action[AnyContent] =
    withAuthentication.async { implicit request: AuthenticatedRequest[AnyContent] =>
      withTicket { implicit ticket =>
        val error = request.flash.get("error")
        val recursiveError = request.flash.get("recursive_error")
        if (recursiveError.isEmpty) {
          if (error.isDefined) {
            Future.successful(Redirect(routes.FrameController.getFrames()).flashing("error" -> error.get))
          } else {
            Future.successful(Redirect(routes.FrameController.getFrames()))
          }
        } else {
          Future.successful(Ok(views.html.container.subject.frame_overview(Seq(), Seq(), recursiveError)))
        }
      }
    }

  /**
   * Right now, this endpoint ignores the query and just redirects to getFrames()
   *
   * @return redirect to getFrames()
   */
  //FIXME ############################################################################################################
  def searchFrames: Action[AnyContent] =
    withAuthentication.async { implicit request: AuthenticatedRequest[AnyContent] =>
      withTicket { implicit ticket =>
        Future.successful(Redirect(routes.FrameController.getFrames()))
      }
    }

  /**
   * Right now, this endpoint ignores the query and just redirects to getFrames()
   *
   * @return redirect to getFrames()
   */
  //FIXME ############################################################################################################
  def findByQuery: Action[AnyContent] =
    withAuthentication.async { implicit request: AuthenticatedRequest[AnyContent] =>
      withTicket { implicit ticket =>
        Future.successful(Redirect(routes.FrameController.getFrames()))
      }
    }

  /**
   * Endpoint to get all [[modules.subject.model.Frame Frames]] the requesting user can access.
   *
   * @param typeSelector  FIXME: this is ignored right now
   * @param groupSelector FIXME: this is ignored right now
   * @return frame overview page
   */
  def getFrames(typeSelector: Option[String] = None, groupSelector: Option[String] = None):
  Action[AnyContent] = withAuthentication.async { implicit request: AuthenticatedRequest[AnyContent] =>
    withTicket { implicit ticket =>
      frameService.getFrameComplex(typeSelector, groupSelector) map (frameComplex => {
        val error = request.flash.get("error")
        Ok(views.html.container.subject.frame_overview(frameComplex.frameTypes, frameComplex.frames, error))
      }) recoverWith {
        case e =>
          logger.error(e.getMessage, e)
          Future.successful(Redirect(routes.FrameController.index()).flashing("recursive_error" -> e.getMessage))
      }
    }
  }

  /**
   * Endpoint to get the [[modules.subject.model.Frame Frame]] detail view (with the Frame and all associated
   * data represented in an [[modules.subject.model.ExtendedFrame ExtendedFrame]] object.
   * <p> The requesting User must have at least the rights to view the Frame.
   *
   * @param frameId id of the Frame
   * @return frame detail page
   */
  def getFrame(frameId: Long): Action[AnyContent] = withAuthentication.async { implicit request: AuthenticatedRequest[AnyContent] =>
    withTicket { implicit ticket =>
      val error = request.flash.get("error")
      (for {
        data <- frameService.getFrame(frameId)
        childTypes <- modelFrameService.getChildren(data._1.frame.typeVersionId)
      } yield {
        Ok(views.html.container.subject.frame_detail_page(data._2, childTypes.map(_.entityType), data._1, error))
      }) recoverWith {
        case e =>
          logger.error(e.getMessage, e)
          Future.successful(Redirect(routes.FrameController.index()).flashing("error" -> e.getMessage))
      }
    }
  }

  /**
   * Endpoint to delete a [[modules.subject.model.Frame Frame]].
   * <p> The Frame is deleted permanently and can not be restored!
   * <p> All contained data will be deleted!
   *
   * @see [[modules.subject.service.FrameService#deleteFrame]]
   * @param frameId id of the Frame to delete
   * @return redirect to getFrames()
   */
  def deleteFrame(frameId: Long): Action[AnyContent] =
    withAuthentication.async { implicit request: AuthenticatedRequest[AnyContent] =>
      withTicket { implicit ticket =>
        frameService.deleteFrame(frameId) map (_ =>
          Redirect(routes.FrameController.getFrames())
          ) recoverWith {
          case e =>
            logger.error(e.getMessage, e)
            Future.successful(Redirect(routes.FrameController.getFrameEditor(frameId)).flashing("error" -> e.getMessage))
        }
      }
    }

  /**
   * Endpoint to get the [[modules.subject.model.Frame Frame]] editor with preloaded data.
   *
   * @param frameId id of the Frame to edit
   * @return frame editor page with preloaded Frame data
   */
  def getFrameEditor(frameId: Long): Action[AnyContent] =
    withAuthentication.async { implicit request: AuthenticatedRequest[AnyContent] =>
      withTicket { implicit ticket =>
        val error = request.flash.get("error")
        updateFrameEditorFactory(frameId, None, error)
      }
    }

  /**
   * Endpoint to post (update) the data of the currently edited [[modules.subject.model.Frame Frame]].
   *
   * @param frameId id of the Frame to edit
   * @return editor view with success or error message
   */
  def postFrame(frameId: Long): Action[AnyContent] =
    withAuthentication.async { implicit request: AuthenticatedRequest[AnyContent] =>
      withTicket { implicit ticket =>
        EntityForm.form.bindFromRequest fold(
          errorForm => updateFrameEditorFactory(frameId, Option(errorForm)),
          data => {
            frameService.updateFrame(frameId, data.values, data.maintainers, data.editors, data.viewers) flatMap (_ => {
              updateFrameEditorFactory(frameId, Some(EntityForm.form.fill(data)), None, Option("Changes saved successfully"))
            }) recoverWith {
              case e: Throwable =>
                logger.error(e.getMessage, e)
                val newAssetForm = EntityForm.form.fill(data)
                updateFrameEditorFactory(frameId, Some(newAssetForm), Option(e.getMessage))
            }
          })
      }
    }

  /**
   * Endpoint to get the state editor of a selected [[modules.subject.model.Frame Frame]].
   * <p> There, the state attribute of the frame can be changed
   *
   * @param frameId id of the parent frame
   * @return frame state editor page
   */
  def getStateEditor(frameId: Long): Action[AnyContent] =
    withAuthentication.async { implicit request: AuthenticatedRequest[AnyContent] =>
      withTicket { implicit ticket =>
        frameService.getSlimFrame(frameId) map (frameHeader => {
          val error = request.flash.get("error")
          val succmsg = request.flash.get("succ")
          Ok(views.html.container.subject.frame_state_graph(frameHeader.frame, error, succmsg))
        }) recoverWith {
          case e: Throwable => Future.successful(Redirect(routes.FrameController.getFrame(frameId)).flashing("error" -> e.getMessage))
        }
      }
    }

  /**
   * Endpoint to change the state of a selected [[modules.subject.model.Frame Frame]].
   *
   * @param frameId id of the parent frame
   * @return frame state editor with error or success message
   */
  def postState(frameId: Long): Action[AnyContent] =
    withAuthentication.async { implicit request: AuthenticatedRequest[AnyContent] =>
      withTicket { implicit ticket =>
        SelectValueForm.form.bindFromRequest fold(
          errorForm => Future.successful(Redirect(routes.FrameController.getStateEditor(frameId)).flashing("error" -> "Invalid form data")),
          data => {
            frameService.updateState(frameId, data.value) map (_ => {
              Redirect(routes.FrameController.getStateEditor(frameId)).flashing("succ" -> "Changes saved successfully")
            }) recoverWith {
              case e: Throwable => Future.successful(Redirect(routes.FrameController.getStateEditor(frameId)).flashing("error" -> e.getMessage))
            }
          })
      }
    }

  /**
   * Endpoint to redirect to a new frame editor of the specified type (by a post request via form submit).
   * <p> Redirects to the equivalent get endpoint with the prepared typeId.
   *
   * @return redirect to getNewFrameEditor() or form with errors
   */
  def requestNewFrameEditor(): Action[AnyContent] =
    withAuthentication.async { implicit request: AuthenticatedRequest[AnyContent] =>
      withTicket { implicit ticket =>
        SelectValueForm.form.bindFromRequest fold(
          errorForm => {
            Future.successful(Redirect(routes.FrameController.index()).flashing("error" -> "Invalid Frame Type input"))
          },
          data => {
            val frameTypeValue = data.value
            modelFrameService.getTypeByValue(frameTypeValue) map (frameType => {
              if (frameType.isEmpty) throw new Exception("No such Frame Type found")
              Redirect(routes.FrameController.getNewFrameEditor(frameType.get.id))
            }) recoverWith {
              case e =>
                logger.error(e.getMessage, e)
                Future.successful(Redirect(routes.FrameController.index()).flashing("error" -> e.getMessage))
            }
          })
      }
    }

  /**
   * Endpoint to get an editor to create new [[modules.subject.model.Frame Frames]].
   * <p> The Editor will only accept Frames of the previously selected [[modules.core.model.EntityType EntityType]].
   *
   * @return new frame editor page
   */
  def getNewFrameEditor(typeId: Long): Action[AnyContent] =
    withAuthentication.async { implicit request: AuthenticatedRequest[AnyContent] =>
      withTicket { implicit ticket =>
        val newEntityForm = EntityForm.form.fill(EntityForm.Data(Seq(), Seq(), Seq(), Seq()))
        val error = request.flash.get("error")
        val success = request.flash.get("success")
        newFrameEditorFactory(typeId, newEntityForm, error, success)
      }
    }

  /**
   * Endpoint to add a new [[modules.subject.model.Frame Frame]].
   * <p> The Frame must be of the selected [[modules.core.model.EntityType EntityTypes]].
   * <p> The incoming form data seq must be in the same order as the previously sent property keys.
   *
   * @see [[modules.subject.service.FrameService#addFrame]]
   * @param typeId id of the parent EntityType
   * @return new frame editor page (clean or with errors)
   */
  def addNewFrame(typeId: Long): Action[AnyContent] =
    withAuthentication.async { implicit request: AuthenticatedRequest[AnyContent] =>
      withTicket { implicit ticket =>
        Future.successful(Redirect(routes.FrameController.index()).flashing("error" -> "Not Implemented yet"))
        EntityForm.form.bindFromRequest fold(
          errorForm => newFrameEditorFactory(typeId, errorForm),
          data => {
            frameService.addFrame(typeId, data.values, data.maintainers, data.editors, data.viewers) map (_ => {
              Redirect(routes.FrameController.getNewFrameEditor(typeId)).flashing("success" -> "Frame successfully created")
            }) recoverWith {
              case e =>
                logger.error(e.getMessage, e)
                val newEntityForm = EntityForm.form.fill(data)
                newFrameEditorFactory(typeId, newEntityForm, Option(e.getMessage))
            }
          })
      }
    }

  /**
   * Helper function to build a 'new frame editor' view based on different configuration parameters.
   *
   * @param typeId  id of the [[modules.core.model.EntityType EntityType]]
   * @param form    NewEntityForm, which can be already filled
   * @param errmsg  optional error message
   * @param succmsg optional positive message
   * @param request implicit request context
   * @return new entity editor result future (view)
   */
  private def newFrameEditorFactory(typeId: Long, form: Form[EntityForm.Data], errmsg: Option[String] = None, succmsg: Option[String] = None)
                                        (implicit request: Request[AnyContent], ticket: Ticket): Future[Result] = {
    for {
      groups <- groupService.getAllGroups
      typeData <- modelFrameService.getLatestExtendedType(typeId)
    } yield {
      Ok(views.html.container.subject.new_frame_editor(typeData.entityType,
        frameService.getFramePropertyKeys(typeData.constraints),
        frameService.getObligatoryPropertyKeys(typeData.constraints),
        groups,
        form, errmsg, succmsg))
    }
  } recoverWith {
    case e =>
      logger.error(e.getMessage, e)
      Future.successful(Redirect(routes.FrameController.index()).flashing("error" -> e.getMessage))
  }

  /**
   * Helper function to build a 'frame editor' view based on different configuration parameters.
   *
   * @param frameId id of the [[modules.subject.model.Frame Frame]] to edit
   * @param form         optional prepared form data
   * @param msg          optional error message
   * @param request      implicit request context
   * @return frame editor page
   */
  private def updateFrameEditorFactory(frameId: Long, form: Option[Form[EntityForm.Data]],
                                            msg: Option[String] = None, successMsg: Option[String] = None)(
                                             implicit request: Request[AnyContent], ticket: Ticket): Future[Result] = {
    for {
      frameHeader <- frameService.getSlimFrame(frameId)
      typeData <- modelFrameService.getExtendedType(frameHeader.frame.typeVersionId)
      groups <- groupService.getAllGroups
    } yield {
      val editForm = if (form.isDefined) form.get else EntityForm.form.fill(
        EntityForm.Data(
          frameHeader.properties.map(_.value),
          frameHeader.viewers.maintainers.toSeq.map(_.name),
          frameHeader.viewers.editors.toSeq.map(_.name),
          frameHeader.viewers.viewers.toSeq.map(_.name)))

      Ok(views.html.container.subject.frame_editor(typeData.entityType,
        frameHeader,
        frameService.getFramePropertyKeys(typeData.constraints),
        frameService.getObligatoryPropertyKeys(typeData.constraints),
        groups,
        editForm, msg, successMsg))
    }
  } recoverWith {
    case e =>
      logger.error(e.getMessage, e)
      Future.successful(Redirect(routes.FrameController.getFrame(frameId)).flashing("error" -> e.getMessage))
  }

}