/*
 * Copyright 2017-2020 47 Degrees, LLC. <http://www.47deg.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package sbtorgpolicies.templates

object utils {

  def safeSubStr(str: String, pos: Int): String = if (pos < str.length) str.substring(pos) else ""

  def safeSubStr(str: String, start: Int, end: Int): String =
    if (start < str.length && end <= str.length && start < end) str.substring(start, end) else ""

  def markdownComment(title: String, start: Boolean = true, scape: Boolean = false): String = {

    def str(s: String, scape: Boolean): String = if (scape) s"\\$s" else s

    (if (start) "\n" else "") +
      str("[", scape) + "comment" + str("]", scape) +
      ": " +
      str("#", scape) + " " +
      str("(", scape) + (if (start) "Start" else "End") + " " + title + str(")", scape)
  }

}
