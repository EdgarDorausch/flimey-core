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
import modules.subject.service.{SubjectService, ModelSubjectService}
import play.api.Logging
import play.api.data.Form
import play.api.i18n.I18nSupport
import play.api.mvc._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

/**
 * Controller to provide all required endpoints to manage [[modules.subject.model.Subject Subjects]].
 *
 * @param cc                      injected ControllerComponents (provides methods and implicits)
 * @param withAuthentication      injected [[middleware.AuthenticationFilter AuthenticationFilter]] to handle session verification
 * @param subjectService      injected [[modules.subject.service.SubjectService SubjectService]]
 * @param modelSubjectService injected [[modules.subject.service.ModelSubjectService ModelSubjectService]]
 */
@Singleton
class SubjectController @Inject()(cc: ControllerComponents,
                                      withAuthentication: AuthenticationFilter,
                                      subjectService: SubjectService,
                                      modelSubjectService: ModelSubjectService) extends
  AbstractController(cc) with I18nSupport with Logging with Authentication {

  /**
   * Endpoint to delete a [[modules.subject.model.Subject Subject]].
   * <p> The Subject is deleted permanently and can not be restored!
   *
   * @param frameId  id of the parent [[modules.subject.model.Frame Frame]]
   * @param subjectId id of the Subject to delete
   * @return redirect to the parent Frame view
   */
  def deleteSubject(frameId: Long, subjectId: Long): Action[AnyContent] =
    withAuthentication.async { implicit request: AuthenticatedRequest[AnyContent] =>
      withTicket { implicit ticket =>
        subjectService.deleteSubject(subjectId) map (_ =>
          Redirect(routes.FrameController.getFrame(frameId))
          ) recoverWith {
          case e =>
            logger.error(e.getMessage, e)
            Future.successful(
              Redirect(routes.SubjectController.getSubjectEditor(frameId, subjectId)
              ).flashing("error" -> e.getMessage))
        }
      }
    }

  /**
   * Endpoint to get the [[modules.subject.model.Subject Subject]] editor with preloaded data.
   *
   * @param frameId  id of the parent [[modules.subject.model.Frame Frame]]
   * @param subjectId id of the Subject to delete
   * @return subject editor page with preloaded Subject data
   */
  def getSubjectEditor(frameId: Long, subjectId: Long): Action[AnyContent] =
    withAuthentication.async { implicit request: AuthenticatedRequest[AnyContent] =>
      withTicket { implicit ticket =>
        val error = request.flash.get("error")
        updateSubjectEditorFactory(frameId, subjectId, None, error)
      }
    }

  /**
   * Endpoint to post (update) the data of the currently edited [[modules.subject.model.Subject Subject]].
   *
   * @param frameId  id of the parent [[modules.subject.model.Frame Frame]]
   * @param subjectId id of the Subject to edit
   * @return subject editor page with preloaded Subject data with success or error message
   */
  def postSubject(frameId: Long, subjectId: Long): Action[AnyContent] =
    withAuthentication.async { implicit request: AuthenticatedRequest[AnyContent] =>
      withTicket { implicit ticket =>
        EntityForm.form.bindFromRequest fold(
          errorForm => updateSubjectEditorFactory(frameId, subjectId, Option(errorForm)),
          data => {
            subjectService.updateSubject(subjectId, data.values) flatMap (_ => {
              updateSubjectEditorFactory(frameId, subjectId, Some(EntityForm.form.fill(data)), None, Option("Changes saved successfully"))
            }) recoverWith {
              case e: Throwable =>
                logger.error(e.getMessage, e)
                val newEntityForm = EntityForm.form.fill(data)
                updateSubjectEditorFactory(frameId, subjectId, Some(newEntityForm), Option(e.getMessage))
            }
          })
      }
    }

  /**
   * Endpoint to get the state editor of a [[modules.subject.model.Subject Subject]].
   *
   * @param frameId  id of the parent [[modules.subject.model.Frame Frame]]
   * @param subjectId id of the Subject to edit
   * @return subject state editor page
   */
  def getStateEditor(frameId: Long, subjectId: Long): Action[AnyContent] =
    withAuthentication.async { implicit request: AuthenticatedRequest[AnyContent] =>
      withTicket { implicit ticket =>
        subjectService.getSubject(subjectId) map (extendedSubject => {
          val error = request.flash.get("error")
          val succmsg = request.flash.get("succ")
          Ok(views.html.container.subject.subject_state_graph(extendedSubject.subject, error, succmsg))
        }) recoverWith {
          case e: Throwable => Future.successful(Redirect(routes.FrameController.getFrame(frameId)).flashing("error" -> e.getMessage))
        }
      }
    }

  /**
   * Endpoint to update the state of a [[modules.subject.model.Subject Subject]].
   *
   * @param frameId  id of the parent [[modules.subject.model.Frame Frame]]
   * @param subjectId id of the Subject to edit
   * @return Subject state editor page (on error) or redirect to Frame overview
   */
  def postState(frameId: Long, subjectId: Long): Action[AnyContent] =
    withAuthentication.async { implicit request: AuthenticatedRequest[AnyContent] =>
      withTicket { implicit ticket =>
        SelectValueForm.form.bindFromRequest fold(
          errorForm => Future.successful(Redirect(routes.SubjectController.getStateEditor(frameId, subjectId)).flashing("error" -> "Invalid form data")),
          data => {
            subjectService.updateState(subjectId, data.value) map (_ => {
              Redirect(routes.SubjectController.getStateEditor(frameId, subjectId)).flashing("succ" -> "Changes saved successfully")
            }) recoverWith {
              case e: Throwable => Future.successful(Redirect(routes.SubjectController.getStateEditor(frameId, subjectId)).flashing("error" -> e.getMessage))
            }
          })
      }
    }

  /**
   * Endpoint to redirect to a new [[modules.subject.model.Subject Subject]] editor of the specified type
   * (by a post request via form submit)
   * <p> Redirects to the equivalent get endpoint with prepared typeId.
   *
   * @param frameId id of the parent [[modules.subject.model.Frame Frame]]
   * @return redirect to getNewSubjectEditor or form with errors
   */
  def requestNewSubjectEditor(frameId: Long): Action[AnyContent] =
    withAuthentication.async { implicit request: AuthenticatedRequest[AnyContent] =>
      withTicket { implicit ticket =>
        SelectValueForm.form.bindFromRequest fold(
          errorForm => {
            Future.successful(Redirect(routes.FrameController.getFrame(frameId)).flashing("error" -> "Invalid Subject Type input"))
          },
          data => {
            val subjectTypeValue = data.value
            modelSubjectService.getTypeByValue(subjectTypeValue) map (subjectType => {
              if (subjectType.isEmpty) Future.failed(new Exception("No such Subject Type found"))
              Redirect(routes.SubjectController.getNewSubjectEditor(frameId, subjectType.get.id))
            })
          } recoverWith {
            case e =>
              logger.error(e.getMessage, e)
              Future.successful(Redirect(routes.FrameController.getFrame(frameId)).flashing("error" -> e.getMessage))
          })
      }
    }

  /**
   * Endpoint to get an editor to create new [[modules.subject.model.Subject Subjects]].
   * <p> The Editor will only accept Subjects of the previously selected [[modules.core.model.EntityType EntityType]].
   *
   * @param frameId id of the parent [[modules.subject.model.Frame Frame]]
   * @return new subject editor page
   */
  def getNewSubjectEditor(frameId: Long, typeId: Long): Action[AnyContent] =
    withAuthentication.async { implicit request: AuthenticatedRequest[AnyContent] =>
      withTicket { implicit ticket =>
        val newEntityForm = EntityForm.form.fill(EntityForm.Data(Seq(), Seq(), Seq(), Seq()))
        val error = request.flash.get("error")
        val success = request.flash.get("success")
        newSubjectEditorFactory(frameId, typeId, newEntityForm, error, success)
      }
    }

  /**
   * Endpoint to add a new [[modules.subject.model.Subject Subject]].
   * <p> The Subject must be of the selected Subject [[modules.core.model.EntityType EntityType]].
   * <p> The incoming form data seq must be in the same order as the previously sent property keys.
   * <p> The parent [[modules.subject.model.Frame Frame]] must support the child subject.
   *
   * @see [[modules.subject.service.SubjectService#addSubject]]
   * @param frameId id of the parent [[modules.subject.model.Frame Frame]]
   * @return new subject editor (clean or with errors)
   */
  def addNewSubject(frameId: Long, typeId: Long): Action[AnyContent] =
    withAuthentication.async { implicit request: AuthenticatedRequest[AnyContent] =>
      withTicket { implicit ticket =>
        EntityForm.form.bindFromRequest fold(
          errorForm => newSubjectEditorFactory(frameId, typeId, errorForm),
          data => {
            subjectService.addSubject(frameId, typeId, data.values) map (_ => {
              Redirect(routes.SubjectController.getNewSubjectEditor(frameId, typeId)).flashing("success" -> "Subject successfully created")
            }) recoverWith {
              case e =>
                logger.error(e.getMessage, e)
                val newEntityForm = EntityForm.form.fill(data)
                newSubjectEditorFactory(frameId, typeId, newEntityForm, Option(e.getMessage))
            }
          })
      }
    }

  /**
   * Helper function to build a 'new subject editor' view based on different configuration parameters.
   *
   * @param frameId id of the parent [[modules.subject.model.Frame Frame]]
   * @param typeId       id of the [[modules.core.model.EntityType EntityType]]
   * @param form         NewEntityForm, which can be already filled
   * @param errmsg       optional error message
   * @param succmsg      optional positive message
   * @param request      implicit request context
   * @return new entity editor result future (view)
   */
  private def newSubjectEditorFactory(frameId: Long, typeId: Long, form: Form[EntityForm.Data], errmsg: Option[String] = None,
                                          succmsg: Option[String] = None)(
                                           implicit request: Request[AnyContent], ticket: Ticket): Future[Result] = {
    modelSubjectService.getLatestExtendedType(typeId) map (typeData => {
      Ok(views.html.container.subject.new_subject_editor(frameId,
        typeData.entityType,
        subjectService.getSubjectPropertyKeys(typeData.constraints),
        subjectService.getObligatoryPropertyKeys(typeData.constraints),
        form, errmsg, succmsg))
    })
  } recoverWith {
    case e =>
      logger.error(e.getMessage, e)
      Future.successful(Redirect(routes.FrameController.getFrame(frameId)).flashing("error" -> e.getMessage))
  }

  /**
   * Helper function to build a 'subject editor' view based on different configuration parameters.
   *
   * @param frameId  id of the parent [[modules.subject.model.Frame Frame]]
   * @param subjectId id of the [[modules.subject.model.Subject Subject]] to edit
   * @param form          optional prepared form data
   * @param msg           optional error message
   * @param request       implicit request context
   * @return frame editor page
   */
  private def updateSubjectEditorFactory(frameId: Long, subjectId: Long, form: Option[Form[EntityForm.Data]],
                                             msg: Option[String] = None, successMsg: Option[String] = None)(
                                              implicit request: Request[AnyContent], ticket: Ticket): Future[Result] = {
    for {
      extendedSubject <- subjectService.getSubject(subjectId)
      typeData <- modelSubjectService.getExtendedType(extendedSubject.subject.typeVersionId)
    } yield {
      val editForm = if (form.isDefined) form.get else EntityForm.form.fill(
        EntityForm.Data(
          extendedSubject.properties.map(_.value), Seq(), Seq(), Seq()))

      Ok(views.html.container.subject.subject_editor(typeData.entityType,
        extendedSubject,
        subjectService.getSubjectPropertyKeys(typeData.constraints),
        subjectService.getObligatoryPropertyKeys(typeData.constraints),
        editForm, msg, successMsg))
    }
  } recoverWith {
    case e =>
      logger.error(e.getMessage, e)
      Future.successful(Redirect(routes.FrameController.getFrame(frameId)).flashing("error" -> e.getMessage))
  }

}