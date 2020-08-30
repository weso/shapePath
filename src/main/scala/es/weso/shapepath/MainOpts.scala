package es.weso.shapepath

import es.weso.shapepath.ShapePath
import es.weso.shex.Schema
import org.rogach.scallop._
import es.weso.rdf.jena.RDFAsJenaModel
import org.apache.jena.rdf.model.ModelFactory

class MainOpts(arguments: Array[String], 
               onError: (Throwable, Scallop) => Nothing) extends ScallopConf(arguments) {

  private lazy val shapePathFormats = ShapePath.availableFormats.toList.map(_.toUpperCase).distinct
  private lazy val defaultShapePathFormat = "Compact"
  private lazy val schemaFormats =
    List("ShExC") ++
      Schema.rdfDataFormats(RDFAsJenaModel(ModelFactory.createDefaultModel)).map(_.toUpperCase).distinct
  private lazy val defaultSchemaFormat = "ShExC"

  banner("""| shapePath: shapePath processor
                | Options:
                |""".stripMargin)

  val shapePath: ScallopOption[String] = opt[String](
    "shapePath",
    short = 'p',
    default = None,
    descr = "Shape path")

  val shapePathFile: ScallopOption[String] = opt[String](
    "shapePathFile",
     noshort = true,
     default = None,
     descr = "Shape path file")

  val shapePathFormat: ScallopOption[String] = opt[String](
    "shapePathFormat",
    noshort = true,
    default = Some(defaultShapePathFormat),
    descr = s"ShapePath format. Default ($defaultShapePathFormat) Possible values: ${showLs(shapePathFormats)}",
    validate = isMemberOf(shapePathFormats))

  val schema: ScallopOption[String] = opt[String](
    "schema",
    short = 's',
    default = None,
    descr = "schema (inline)")

  val schemaFile: ScallopOption[String] = opt[String](
    "schema file",
    short = 'f',
    default = None,
    descr = "schema file")

  val schemaFormat: ScallopOption[String] = opt[String](
    "schema format",
    noshort = true,
    default = Some(defaultSchemaFormat),
    descr = s"Schema format. Default ($defaultSchemaFormat) Possible values: ${showLs(schemaFormats)}",
    validate = isMemberOf(schemaFormats))

  val schemaUrl: ScallopOption[String] = opt[String](
    "schemaUrl",
    short = 'u',
    default = None,
    descr = "schema Url")


  footer("Enjoy!")

  val manifest: ScallopOption[String] = opt[String](
    "manifest",
    default = None,
    descr = "Manifest file to test",
    short = 'm')

  private def showLs(ls: List[String]): String =
    ls.mkString(",")

  private def isMemberOf(ls: List[String])(x: String): Boolean =
    ls.map(_.toUpperCase()) contains x.toUpperCase

}
