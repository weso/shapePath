package es.weso.shapepath
import java.net.URI

import cats.MonadError
import cats.effect._
import cats.syntax.all._
import es.weso.shex.Schema

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext
import org.rogach.scallop._
import org.rogach.scallop.exceptions._

import scala.io.Source

object Main {

  implicit val timer: Timer[IO] = IO.timer(ExecutionContext.global)

  private def program(args: Array[String]): IO[Unit] = {
    val opts = new MainOpts(args, errorDriver)
    for {
      _ <- IO(opts.verify())
      shapePath <- getShapePath(opts).onError(handleErr(s"Error processing shapePath"))
      schema <- getSchema(opts).onError(handleErr(s"Error processing schema"))
      _ <- run(shapePath, schema, opts)
    } yield ()
  }

  private def handleErr(msg: String): PartialFunction[Throwable, IO[Unit]] = {
    case e => IO(println(s"${msg}: ${e.getMessage}"))
  }

  private def getShapePath(opts: MainOpts): IO[ShapePath] = {
    if (opts.shapePath.isDefined) {
      es2io(ShapePath.fromString(opts.shapePath(), opts.shapePathFormat(), None))
    }
    else if (opts.shapePathFile.isDefined)
      for {
        contents <- IO(Source.fromFile(opts.shapePathFile()))
        sp <- es2io(ShapePath.fromString(contents.mkString, opts.shapePathFormat(), None))
      } yield sp
    else for {
      _ <- IO(println(s"Warning: No shapePath or shapePath provided. Default to empty shapePath"))
    } yield ShapePath.empty
  }

  private def getSchema(opts: MainOpts): IO[Schema] = {
    if (opts.schema.isDefined) {
      Schema.fromString(opts.schema(), opts.schemaFormat(), None)
    }
    else if (opts.schemaFile.isDefined)
      for {
        contents <- IO(Source.fromFile(opts.schemaFile()))
        s <- Schema.fromString(contents.mkString, opts.schemaFormat(), None)
      } yield s
    else if (opts.schemaUrl.isDefined) for {
      contents <- IO(Source.fromURI(new URI(opts.schemaUrl())))
      s <- Schema.fromString(contents.mkString, opts.schemaFormat(), None)
    } yield s
    else for {
      _ <- IO(println(s"Warning: No schema or schemaFile provided. Default to empty schema"))
    } yield Schema.empty
  }

  private def run(shapePath: ShapePath, schema: Schema, opts: MainOpts): IO[Unit] = {
    val (errors, value) = ShapePath.eval(shapePath,schema,None)
    for {
      _ <- if (errors.nonEmpty) IO(println(s"${errors.map(e => s"$e\n").mkString}"))
           else IO(())
      _ <- IO(println(s"Value: ${value.show}"))
    } yield ()
  }

  def main(args: Array[String]): Unit =
    program(args).unsafeRunSync

  private def errorDriver(e: Throwable, scallop: Scallop) = e match {
    case Help(s) => {
      println(s"Help: $s")
      scallop.printHelp
      sys.exit(0)
    }
    case _ => {
      println(s"Error: ${e.getMessage}")
      scallop.printHelp
      sys.exit(1)
    }
  }

  private def es2io[A](es: Either[String,A]): IO[A] =
    MonadError[IO,Throwable].rethrow(IO(es.leftMap(new RuntimeException(_))))

}
