import org.scalajs.linker.interface.OutputPatterns

inThisBuild(
  List(
    scalaVersion := "3.0.1",
    evictionErrorLevel := Level.Warn,
    publish / skip := true,
    scalafmtSbt := true,
    // metadata
    organization := "dev.sacode",
    licenses := List("GPL-3.0" -> url("https://www.gnu.org/licenses/gpl-3.0.html")),
    scmInfo := Some(
      ScmInfo(
        url("https://github.com/sacode387/FlowRun"),
        "scm:git:git@github.com:sacode387/FlowRun.git"
      )
    ),
    developers := List(
      Developer(
        "sake92",
        "Sakib Hadžiavdić",
        "sakib@sake.ba",
        url("https://sake.ba")
      )
    )
  )
)

lazy val core = (project in file("core"))
  .settings(
    name := "FlowRun",
    libraryDependencies ++= Seq(
      "org.scala-js" %%% "scalajs-dom" % "1.1.0", // not yet available
      "com.lihaoyi" %%% "scalatags" % "0.9.4", // not yet available
      "com.lihaoyi" %%% "pprint" % "0.6.6" // sourcecode from scalatags... 2.13
    ).map(_.cross(CrossVersion.for3Use2_13)),
    libraryDependencies ++= Seq(
      "com.outr" %%% "reactify" % "4.0.6",
      "org.getshaka" %%% "native-converter" % "0.5.2",
      "com.lihaoyi" %%% "utest" % "0.7.10" % Test
    ),
    scalacOptions ++= Seq(
      "-Xmax-inlines",
      "64",
      "-Ysafe-init",
      "-deprecation"
    ),
    scalaJSLinkerConfig ~= { _.withModuleKind(ModuleKind.ESModule) },

    // tests
    testFrameworks += new TestFramework("utest.runner.Framework"),
    Test / parallelExecution := false,
    Test / scalaJSLinkerConfig ~= { _.withModuleKind(ModuleKind.NoModule) },
    Test / jsEnv := new org.scalajs.jsenv.jsdomnodejs.JSDOMNodeJSEnv()
  )
  .enablePlugins(ScalaJSPlugin)

lazy val demo = (project in file("demo"))
  .settings(
    scalaJSUseMainModuleInitializer := true,
    (Compile / compile) := {
      WebKeys.assets.value
      (Compile / compile).value
    },
    Compile / fastLinkJS / scalaJSLinkerOutputDirectory :=
      (Assets / WebKeys.public).value / "scripts"
  )
  .dependsOn(core)
  .enablePlugins(ScalaJSPlugin, SbtWeb)
