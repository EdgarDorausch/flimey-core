/*
 * This file is part of the flimey-core software.
 * Copyright (C) 2021 Edgar Dorausch
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

package util.data

import modules.util.data.StringProcessor
import org.scalatestplus.play.PlaySpec

class StringProcessor_UnitSpec extends PlaySpec {

  private object helper extends StringProcessor

  "isNumericString" must {
    "return true on empty string" in {
      helper.isNumericString("") mustBe true
    }

    "return true on number in scientific notation" in {
      helper.isNumericString("10,89e-31") mustBe true
      helper.isNumericString("1089e+3") mustBe true
      helper.isNumericString("9E+3") mustBe true
    }

    "return false on number in ill formed scientific notation" in {
      helper.isNumericString("e-31") mustBe false
      helper.isNumericString("1e3") mustBe false
      helper.isNumericString("9E") mustBe false
    }

    "return true on numbers with sign" in {
      helper.isNumericString("+712.8090") mustBe true
      helper.isNumericString("-712.8090") mustBe true
      helper.isNumericString("+7128090") mustBe true
      helper.isNumericString("+7128090,9000") mustBe true
    }

    "return false on wrong sign usage" in {
      helper.isNumericString("+") mustBe false
      helper.isNumericString("-") mustBe false
      helper.isNumericString("+.89") mustBe false
      helper.isNumericString("7878+") mustBe false
    }

    "return true on right comma and point usages" in {
      helper.isNumericString(".01") mustBe true
      helper.isNumericString("12.00") mustBe true
      helper.isNumericString(",01") mustBe true
      helper.isNumericString("12,00") mustBe true
      helper.isNumericString("12,000,000.0") mustBe true
      helper.isNumericString("12.000.000,0") mustBe true
    }

    "return false on wrong comma and point usages" in {
      helper.isNumericString("1.,0") mustBe false
      helper.isNumericString("1.") mustBe false
      helper.isNumericString("1,") mustBe false
      helper.isNumericString("+.,") mustBe false
      helper.isNumericString("1.E-4") mustBe false
      helper.isNumericString("1,E-4") mustBe false
    }

    "return false on nonsense" in {
      helper.isNumericString("foo") mustBe false
      helper.isNumericString("8098foo") mustBe false
      helper.isNumericString("1.0o") mustBe false
      helper.isNumericString("1 8979 7987, 0") mustBe false
      helper.isNumericString("1.0 $") mustBe false

    }
  }
}