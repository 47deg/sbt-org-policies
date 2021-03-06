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

package sbtorgpolicies.settings

import com.jsuereth.sbtpgp.PgpKeys
import com.jsuereth.sbtpgp.PgpKeys._
import sbtorgpolicies.runnable.RunnableItemConfigScope
import sbtorgpolicies.runnable.syntax._
import scoverage.ScoverageKeys
import org.scalajs.sbtplugin.ScalaJSPlugin.autoImport._
import sbt.Keys._
import sbt._
import sbtorgpolicies._
import sbtorgpolicies.model._
import OrgPoliciesKeys._
import sbtrelease.ReleasePlugin.autoImport.ReleaseTransformations._
import sbtrelease.ReleasePlugin.autoImport._
import sbtunidoc.BaseUnidocPlugin.autoImport._
import sbtunidoc.ScalaUnidocPlugin.autoImport._
import xerial.sbt.Sonatype.SonatypeKeys.sonatypePublishToBundle

trait AllSettings
    extends dependencies
    with scalafmt
    with files
    with fileValidation
    with enforcement
    with bash
    with release
    with common
    with utils
    with ScalaSettings {

  /**
   * Settings common to all projects.
   *
   * Adds Sonatype release repository and "withCachedResolution" to the update options
   */
  lazy val sharedCommonSettings = Seq(
    resolvers ++= Seq(Resolver.sonatypeRepo("releases")),
    updateOptions := updateOptions.value.withCachedResolution(true)
  )

  /**
   * Common dependencies to all projects.
   *
   */
  lazy val sharedCommonDependencies = Seq(
    libraryDependencies ++= Seq(
      compilerPlugin(%%("kind-projector"))
    )
  )

  /**
   * Release settings common to all projects.
   *
   * Adds a Sonatype release step to the default release steps
   */
  lazy val sharedReleaseProcess = Seq(
    releaseProcess := Seq[ReleaseStep](
      orgInitialVcsChecks,
      checkSnapshotDependencies,
      orgInquireVersions,
      if (sbtPlugin.value) releaseStepCommandAndRemaining("^ clean") else runClean,
      if (sbtPlugin.value) releaseStepCommandAndRemaining("^ test") else runTest,
      orgTagRelease,
      orgUpdateChangeLog,
      if (sbtPlugin.value) releaseStepCommandAndRemaining("^ publishSigned") else publishArtifacts,
      releaseStepCommandAndRemaining("sonatypeBundleRelease"),
      setNextVersion,
      orgCommitNextVersion,
      orgPostRelease
    )
  )

  /** Adds the credential settings required for sonatype releases.*/
  lazy val credentialSettings = Seq(
    credentials ++= (for {
      username <- getEnvVar("SONATYPE_USERNAME")
      password <- getEnvVar("SONATYPE_PASSWORD")
    } yield Credentials(
      "Sonatype Nexus Repository Manager",
      "oss.sonatype.org",
      username,
      password
    )).toSeq
  )

  /**
   * Common PGP settings, needed to sign the artifacts when publishing them.
   */
  lazy val pgpSettings = Seq(
    pgpPassphrase := sys.env.get("PGP_PASSPHRASE").map(_.toCharArray)
  )

  /** Settings to make the module not published*/
  lazy val noPublishSettings = Seq(
    publish := ((): Unit),
    publishLocal := ((): Unit),
    publishArtifact := false,
    publishMavenStyle := false // suppress warnings about intransitive deps (not published anyway)
  )

  lazy val scalaMetaSettings = Seq(
    addCompilerPlugin(%%("scalameta-paradise") cross CrossVersion.full),
    libraryDependencies += %%("scalameta"),
    scalacOptions += "-Xplugin-require:macroparadise",
    scalacOptions in (Compile, console) ~= (_ filterNot (_ contains "paradise")) // macroparadise plugin doesn't work in repl yet.
  )

  /**
   * Scala JS settings shared by many projects.
   *
   * Forces the use of node.js in tests and batchmode under travis
   */
  lazy val sharedJsSettings = Seq(
    scalaJSStage in Global := FastOptStage,
    parallelExecution := false,
    jsEnv := new org.scalajs.jsenv.nodejs.NodeJSEnv(),
    // batch mode decreases the amount of memory needed to compile scala.js code
    scalaJSOptimizerOptions := scalaJSOptimizerOptions.value.withBatchMode(
      getEnvVar("TRAVIS").isDefined
    )
  )

  /**
   * Build settings common to all projects.
   *
   * Uses the github settings and versions map to set the organisation,
   * scala version and cross versions
   */
  val sharedBuildSettings = Seq(
    organization := orgGithubSetting.value.groupId,
    organizationName := orgGithubSetting.value.organizationName,
    homepage := Option(orgGithubSetting.value.homePage),
    organizationHomepage := Option(orgGithubSetting.value.organizationHomePage),
    startYear := Some(currentYear),
    scalaOrganization := "org.scala-lang",
    scalaVersion := scalac.`2.12`,
    crossScalaVersions := scalac.crossScalaVersions,
    scalacOptions ++= scalacAllOptions(scalaVersion.value),
    Compile / console / scalacOptions -= "-Xlint"
  )

  implicit val settingAppender: sbt.Append.Value[Seq[(String, java.net.URL)], License] =
    new sbt.Append.Value[Seq[(String, URL)], License] {
      override def appendValue(a: Seq[(String, URL)], b: License): Seq[(String, URL)] =
        a :+ b.tupled
    }

  /**
   * Publish settings common to all projects.
   *
   * Uses the github settings and list of developers to set all publish settings
   * required to publish signed artifacts to Sonatype OSS repository
   */
  val sharedPublishSettings: Seq[Setting[_]] = Seq(
    homepage := Some(url(orgGithubSetting.value.home)),
    licenses += orgLicenseSetting.value,
    scmInfo := Some(
      ScmInfo(url(orgGithubSetting.value.home), "scm:git:" + orgGithubSetting.value.repo)
    ),
    apiURL := Some(url(orgGithubSetting.value.api)),
    releaseCrossBuild := true,
    releasePublishArtifactsAction := PgpKeys.publishSigned.value,
    publishMavenStyle := true,
    publishArtifact in Test := false,
    pomIncludeRepository := Function.const(false),
    publishTo := sonatypePublishToBundle.value,
    autoAPIMappings := true,
    pomExtra := <developers> {
      (orgMaintainersSetting.value ++ orgContributorsSetting.value).map(_.pomExtra)
    } </developers>
  )

  /** Common coverage settings, with minimum coverage defaulting to 80.*/
  def sharedScoverageSettings(min: Double = scoverageMinimum) = Seq(
    ScoverageKeys.coverageMinimum := min,
    ScoverageKeys.coverageFailOnMinimum := true,
    ScoverageKeys.coverageHighlighting := scalaBinaryVersion.value != "2.10"
  )

  lazy val scalafmtSettings: Seq[Setting[_]] =
    orgAutomateScalafmtFor(Compile, Test)

  /** Common unidoc settings, adding the "-Ymacro-no-expand" scalac option.*/
  lazy val unidocCommonSettings = Seq(
    scalacOptions in (ScalaUnidoc, unidoc) += "-Ymacro-no-expand"
  )

  /** Add the "unused import" warning to scala 2.11+, but not for the console.*/
  lazy val warnUnusedImport = Seq(
    scalacOptions ++= {
      CrossVersion.partialVersion(scalaVersion.value) match {
        case Some((2, 11)) | Some((2, 12)) =>
          Seq("-Ywarn-unused-import")
        case Some((2, n)) if n >= 13 =>
          Seq("-Ywarn-unused:imports")
        case _ =>
          Nil
      }
    },
    scalacOptions in (Compile, console) -= "-Ywarn-unused-import",
    scalacOptions in (Compile, console) -= "-Ywarn-unused:imports",
    scalacOptions in (Test, console) := { scalacOptions in (Compile, console) }.value
  )

  /**
   * Add a "pretty shell prompt". Do not use this settings if you are in Emacs sbt-mode:
   * https://github.com/ensime/emacs-sbt-mode , since it's incompatible.
   */
  lazy val shellPromptSettings = Seq(
    shellPrompt := { s: State =>
      val c     = scala.Console
      val blue  = c.RESET + c.BLUE + c.BOLD
      val white = c.RESET + c.BOLD

      val projectName = Project.extract(s).currentProject.id

      s"$blue$projectName$white>${c.RESET}"
    }
  )

  /**
   * Alias helper for the publishMicrosite task when docs module is located in the "docs" sbt module.
   */
  lazy val defaultPublishMicrosite: RunnableItemConfigScope[Unit] =
    ";project docs;publishMicrosite".asRunnableItem

}
