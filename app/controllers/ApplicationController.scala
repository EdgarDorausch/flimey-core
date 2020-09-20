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

import javax.inject._
import middleware.Authentication
import play.api.Logging
import play.api.mvc._

@Singleton
class ApplicationController @Inject()(cc: ControllerComponents) extends AbstractController(cc) with Logging with Authentication {

  def index() = Action { implicit request: Request[AnyContent] =>
    //if(hasSession) {
    //  Ok(views.html.app()()())
    //} else {
    //  Redirect(routes.AuthController.getLoginPage())
    //}
    Redirect(routes.AssetController.index())
  }

}
