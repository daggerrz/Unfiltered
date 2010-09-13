import sbt._

class Unfiltered(info: ProjectInfo) extends ParentProject(info) with posterous.Publish {

  class UnfilteredModule(info: ProjectInfo) extends DefaultProject(info) with sxr.Publish {
    override def packageSrcJar= defaultJarPath("-sources.jar")
    lazy val sourceArtifact = Artifact.sources(artifactID)
    override def packageToPublishActions = super.packageToPublishActions ++ Seq(packageSrc)
  }

  /** Allows Unfiltered modules to test themselves using other modules */
  trait IntegrationTesting extends DefaultProject {
    // add to test classpath manually since we don't want to actually depend on these modules
    override def testClasspath = (super.testClasspath /: (spec :: jetty :: filter_p :: Nil)) {
      _ +++ _.projectClasspath(Configurations.Compile)
    }
    override def testCompileAction = super.testCompileAction dependsOn 
      (spec.compile, jetty.compile, filter_p.compile)
    lazy val specs = specsDependency % "test"
    lazy val dispatch = dispatchDependency % "test"
    lazy val jetty7 = jettyDependency % "test"
  }

  /** core unfiltered library */
  lazy val library = project("library", "Unfiltered",
      new UnfilteredModule(_) with IntegrationTesting {
    val codec = "commons-codec" % "commons-codec" % "1.4"
  })
  lazy val filter_p = project("filter", "Unfiltered Filter", new UnfilteredModule(_) with IntegrationTesting {
    lazy val filter = servletApiDependency
  }, library)
  /** file uploads */
  lazy val uploads = project("uploads", "Unfiltered Uploads", new UnfilteredModule(_) with IntegrationTesting{
    lazy val filter = servletApiDependency
    val io = "commons-io" % "commons-io" % "1.4"
    val fileupload = "commons-fileupload" % "commons-fileupload" % "1.2.1"
  }, filter_p)
  val jetty_version = "7.0.2.v20100331"
  /** embedded server*/
  lazy val jetty = project("jetty", "Unfiltered Jetty", new UnfilteredModule(_) {
    val jetty7 = jettyDependency
  }, filter_p)
  /** AJP protocol server */
  lazy val jetty_ajp = project("jetty-ajp", "Unfiltered Jetty AJP", new UnfilteredModule(_) {
    val jetty7 = "org.eclipse.jetty" % "jetty-ajp" % jetty_version
  }, jetty)

  /** Marker for demos that should not be published */
  trait Demo
  /** Marker for Scala 2.8-only projects that shouldn't be cross compiled or published */
  trait Only28

  /** demo project */
  lazy val demo = project("demo", "Unfiltered Demo", new UnfilteredModule(_) with Demo, jetty)

  /** specs  helper */
  lazy val spec = project("spec", "Unfiltered Spec", new DefaultProject(_) with sxr.Publish {
    lazy val specs = specsDependency
    lazy val dispatch = dispatchDependency
  }, jetty)
  /** json extractors */
  lazy val json = project("json", "Unfiltered Json", 
      new UnfilteredModule(_) with IntegrationTesting {
    val lift_json = "net.liftweb" %% "lift-json" % "2.1-M1"
  }, library)

  def servletApiDependency = "javax.servlet" % "servlet-api" % "2.3" % "provided"

  lazy val scalateDemo = project("demo-scalate", "Unfiltered Scalate Demo", new UnfilteredModule(_) with Only28 with Demo {
    val slf4j = "org.slf4j" % "slf4j-simple" % "1.6.0"
  }, jetty, scalate)

  lazy val scalate = project("scalate", "Unfiltered Scalate", 
      new UnfilteredModule(_) with Only28 with IntegrationTesting {
    val scalateLibs = "org.fusesource.scalate" % "scalate-core" % "1.2"
    val scalaCompiler = "org.scala-lang" % "scala-compiler" % "2.8.0" % "test"
    override def repositories = Set(ScalaToolsSnapshots)
  }, library)
  
  def specsDependency =
    if (buildScalaVersion startsWith "2.7.")
      "org.scala-tools.testing" % "specs" % "1.6.2.2"
    else
      "org.scala-tools.testing" %% "specs" % "1.6.5"

  def dispatchDependency = "net.databinder" %% "dispatch-mime" % "0.7.6"
  
  def jettyDependency = "org.eclipse.jetty" % "jetty-webapp" % jetty_version

  /** Exclude demo from publish and all other actions run from parent */
  override def dependencies = super.dependencies.filter { 
    case _: Demo => false
    case _: Only28 => buildScalaVersion startsWith "2.8"
    case _ => true
  }

  override def postTitle(vers: String) = "Unfiltered %s" format vers

  override def managedStyle = ManagedStyle.Maven
  val publishTo = "Scala Tools Nexus" at "http://nexus.scala-tools.org/content/repositories/releases/"
  Credentials(Path.userHome / ".ivy2" / ".credentials", log)
}
