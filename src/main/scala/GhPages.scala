import sbt._
import Keys._

object GhPages extends Plugin {

  object ghpages {
    val Config = config("ghpages") extend(Runtime)
    lazy val pagesRepository = SettingKey[File]("pages-repository") in Config
    lazy val updatedPagesRepository = TaskKey[File]("updated-pages-repository") in Config
    lazy val siteDirectory = SettingKey[File]("site-directory") in Config
    lazy val copyAPIDoc = TaskKey[File]("copy-api-doc") in Config
    lazy val copySite = TaskKey[File]("copy-site") in Config
    lazy val genSite = TaskKey[Unit]("gen-site") in Config
    lazy val cleanSite = TaskKey[Unit]("clean-site") in Config
    lazy val pushAPIDoc = TaskKey[Unit]("push-api-doc") in Config
    lazy val pushSite = TaskKey[Unit]("push-site") in Config
    lazy val gitRemoteRepo = SettingKey[String]("git-remote-repo") in Config

    def settings: Seq[Setting[_]] = Seq(
      //gitRemoteRepo := "git@github.com:jsuereth/scala-arm.git",
      pagesRepository <<= (name,organization) apply ((n,o) => file(System.getProperty("user.home")) / ".sbt" / "ghpages" / o / n),
      updatedPagesRepository <<= updatedRepo(pagesRepository, gitRemoteRepo, Some("gh-pages")),
      pushAPIDoc <<= pushAPIDoc0,
      pushSite <<= pushAPIDoc map (_ => ()), // Do nothing but push API docs for now.... TODO - Allow more on the site...
      siteDirectory <<= target(_ / "site"),
      copyAPIDoc <<= copyAPIDoc0,
      copySite <<= copySite0,
      cleanSite <<= cleanSite0,
      // For now, assume nothing and let projects override.
      genSite := ()
    )
    private def updatedRepo(repo: ScopedSetting[File], remote: ScopedSetting[String], branch: Option[String]) =
       (repo, remote, streams) map { (local, uri, s) => updated(remote = uri, cwd = local, branch = branch, log = s.log); local }

    private def copySite0 = (siteDirectory, updatedPagesRepository, genSite, streams) map { (dir, repo, _, s) =>
      // TODO - Should we even attempt to clean?
      IO.copyDirectory(dir, repo)
      repo
    }

    private def cleanSite0 = (updatedPagesRepository, streams) map { (dir, s) =>
      val toClean = IO.listFiles(dir).map(_.getAbsolutePath).filter(!_.contains("\\.git")).toList
      git(("rm" :: "-r" :: "--ignore-unmatch" :: toClean) :_*)(dir, s.log)
      ()
    }

    private def copyAPIDoc0 = (updatedPagesRepository, doc in Compile, streams) map { (repo, newAPI, s) =>
      git("rm", "-r", "--ignore-unmatch", "latest")(repo, s.log)
      if(repo / "latest" exists) {
        IO.delete(repo / "latest")
      }
      IO.copyDirectory(newAPI, repo / "latest" / "api")
      //IO.copyDirectory(newSXR, repo / "latest" / "sxr")
      repo
    }
    private def pushAPIDoc0 = (copyAPIDoc, streams) map { (repo, s) => commitAndPush("updated api documentation")(repo, s.log) }
    private def commitAndPush(msg: String, tag: Option[String] = None)(repo: File, log: Logger) {
      git("add", ".")(repo, log)
      git("commit", "-m", msg, "--allow-empty")(repo, log)
      for(tagString <- tag) git("tag", tagString)(repo, log)
      push(repo, log)
    }
    private def push(cwd: File, log: Logger) = git("push")(cwd, log)
    private def pull(cwd: File, log: Logger) = git("pull")(cwd, log)
    private def updated(remote: String, branch: Option[String], cwd: File, log: Logger): Unit =
      if(cwd.exists) pull(cwd, log)
      else branch match {
        case None => git("clone", remote, ".")(cwd, log)
        case Some(b) => git("clone", "-b", b, remote, ".")(cwd, log)
      }

    private def git(args: String*)(cwd: File, log: Logger): Unit = {
      IO.createDirectory(cwd)
      val full = "git" +: args
      log.info(cwd + "$ " + full.mkString(" "))
      val code = Process(full, cwd) ! log
      if(code != 0) error("Nonzero exit code for git " + args.take(1).mkString + ": " + code)
    }

    /** TODO - Create ghpages in the first place if it doesn't exist.
        $ cd /path/to/fancypants
        $ git symbolic-ref HEAD refs/heads/gh-pages
        $ rm .git/index
        $ git clean -fdx
        <copy api and documentation>
        $ echo "My GitHub Page" > index.html
        $ git add .
        $ git commit -a -m "First pages commit"
        $ git push origin gh-pages
     */
  }
}