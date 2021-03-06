package es.weso.shapepath

import cats._
import cats.data.{NonEmptyList, Writer}
import cats.instances.list._
import cats.syntax.applicative._
import cats.syntax.foldable._
import cats.syntax.show._
import cats.syntax.writer._
import es.weso.rdf.nodes.{BNode, IRI}
import es.weso.shapepath.compact.Parser
import es.weso.shex.{EachOf, IRILabel, NodeConstraint, OneOf, Schema, Shape, ShapeAnd, ShapeExpr, ShapeLabel, ShapeNot, ShapeOr, TripleConstraint, TripleExpr}
import Step._
import cats.implicits.catsSyntaxFlatMapOps
import es.weso.shex.implicits.showShEx._

case class ShapePath(startsWithRoot: Boolean, steps: List[Step])

object ShapePath {

  def empty: ShapePath = ShapePath(startsWithRoot = false,List())

  /**
   * Evaluate a shapePath
   * @param p shape path
   * @param s schema
   * @param maybeValue initial value. If not provided it is initialized to the list of shapes in s
   * @return A pair containing a list of processing errors (empty if no error) and a result value
   */
  def eval(p: ShapePath,
           s: Schema,
           maybeValue: Option[Value] = None
          ): (List[ProcessingError], Value) = {
    evaluateShapePath(p, s,
      maybeValue.getOrElse(Value(s.localShapes.map(ShapeExprItem(_))))
    ).run
  }

  lazy val availableFormats : NonEmptyList[String] = NonEmptyList("Compact", List())
  lazy val defaultFormat: String = availableFormats.head

  /**
   * Parse a shapePath from a string
   * @param str
   * @param format
   * @param base
   * @return either an error string or a ShapePath
   */
  def fromString(str: String,
                 format: String = "Compact",
                 base: Option[IRI] = None): Either[String, ShapePath] =
   format.toLowerCase match {
    case "compact" => Parser.parseShapePath(str,base)
    case _ => Left(s"Unsupported input format: $format")
   }

  private type Comp[A] = Writer[List[ProcessingError],A]

  private def ok[A](x: A): Comp[A] = x.pure[Comp]

  private def err(msg: String): Comp[Unit] = {
    val error: List[ProcessingError] = List(Err(msg))
    for {
     _ <- error.tell
    } yield ()
  }

  private def warning(msg: String): Comp[Unit] = {
    val warning: List[ProcessingError] = List(Warning(msg))
    for {
      _ <- warning.tell
    } yield ()
  }


  private def debug(msg: String): Comp[Unit] = {
    println(s"DEBUG: $msg")
    ().pure[Comp]
  }

/*  private def checkContext(ctx: Context)(item: Item): Boolean = ctx match {
    case ShapeAndCtx => item match {
      case ShapeExprItem(se) => se match {
        case _: ShapeAnd => true
        case _ => false
      }
      case _ => false
    }
    case ShapeOrCtx => item match {
      case ShapeExprItem(se) => se match {
        case _: ShapeOr => true
        case _ => false
      }
      case _ => false
    }
    case ShapeNotCtx => item match {
      case ShapeExprItem(se) => se match {
        case _: ShapeNot => true
        case _ => false
      }
      case _ => false
    }
    case NodeConstraintCtx => item match {
      case ShapeExprItem(se) => se match {
        case _: NodeConstraint => true
        case _ => false
      }
      case _ => false
    }
    case ShapeCtx => item match {
      case ShapeExprItem(se) => se match {
        case _: Shape => true
        case _ => false
      }
      case _ => false
    }
    case EachOfCtx => item match {
      case TripleExprItem(te) => te match {
        case _:EachOf => true
        case _ => false
      }
      case _ => false
    }
    case OneOfCtx => item match {
      case TripleExprItem(te) => te match {
        case _:OneOf => true
        case _ => false
      }
      case _ => false
    }
    case TripleConstraintCtx => item match {
      case TripleExprItem(te) => te match {
        case _:TripleConstraint => true
        case _ => false
      }
      case _ => false
    }
  } */
  private def checkContext(context: Context, item: Item): Comp[List[Item]] = ???

  private def matchShapeExprId(lbl: ShapeLabel)(se: ShapeExpr): Boolean = se.id match {
    case None => false
    case Some(idLbl) => idLbl == lbl
  }

  private def noValue: Value = Value(List())

  private def hasPredicate(expr: TripleExpr, label: ShapeLabel): Comp[Boolean] = expr match {
    case tc: TripleConstraint => label match {
      case i: IRILabel => ok(i.iri == tc.predicate)
      case _ => err(s"Label: ${label.show} must be an IRI") >> ok(false)
    }
    case _ => ok(false)
  }

  /**
   * Get element from a list at position n
   * If the list doesn't have enough elements, returns the empty list
   * @param ls list
   * @param n position
   * @tparam A
   * @return List containing the element or empty
   *         Examples:
   *         getElementAt(List['a','b'.'c'], 2) = List('b')
   *         getElementAt(List['a','b'.'c'], 5) = List()
   */
  private def getElementAt[A](ls: List[A], n: Int): List[A] =
    ls.slice(n - 1, n)

  private def undef(msg: String, current: Value): Comp[Value] = err(msg) >> ok(current)

  private def evaluateIndex(items: List[Item], index: ExprIndex): Comp[Value] = {
    val zero: Value = noValue
    def cmb(current: Value, item: Item): Comp[Value] =
      item match {
        /*      case SchemaItem(s) => index match {
        case IntShapeIndex(idx) => Value(s.localShapes.slice(idx - 1,1).map(ShapeExprItem(_))).pure[Comp]
        case ShapeLabelIndex(lbl) => Value(s.localShapes.filter(matchShapeExprId(lbl)).map(ShapeExprItem(_))).pure[Comp]
        case _ => warning(s"Index ${index.show} applied to schema item ${s.show}")
      } */
        case ShapeExprItem(se) => se match {
          case sa: ShapeAnd => index match {
            case IntShapeIndex(n) => ok(current.add(getElementAt(sa.shapeExprs,n).map(ShapeExprItem(_))))
            case _ =>
              // warning(s"ShapeAnd: evaluating index ${index.show} returns no item") >>
              ok(current)
          }
          case so: ShapeOr => index match {
            case IntShapeIndex(n) => ok(current.add(getElementAt(so.shapeExprs,n).map(ShapeExprItem(_))))
            case _ =>
              // warning(s"ShapeOr: evaluating index ${index.show} returns no item") >>
              ok(current)
          }
          case _: ShapeNot =>
            warning(s"ShapeOr: evaluating index ${index.show} returns no item") >>
            ok(current)
          case s: Shape => index match {
            case ShapeLabelIndex(lbl) => s.id match {
              case None =>
                warning(s"Index: ${index.show} accessing shape without label") >>
                ok(current)
              case Some(slbl) =>
                if (slbl == lbl)
                  ok(current.add(s))
                else
                  warning(s"Index: ${index.show} accessing shape with label ${slbl.show}") >>
                  ok(current)
            }
            case _ : LabelTripleExprIndex | _ : IntTripleExprIndex => s.expression match {
              case None =>
                warning(s"Index: ${index.show} accessing shape without expression: ${s.show}") >>
                ok(current)
              case Some(te) =>
                evaluateIndex(List(TripleExprItem(te)), index).flatMap(newValue =>
                ok(current.add(newValue))
              )
            }
            case _ => err(s"evaluateIndex: Match index shape: Unimplemented index ${index}") >>
                      ok(current)
          }
          case _ => err(s"evaluateIndex: Unimplemented ShapeExprItem(${se.show})") >>
                    ok(current)
        }
        case TripleExprItem(te) => te match {
          case eo: EachOf => index match {
            case LabelTripleExprIndex(lbl,maybeN) => for {
              tcs <- TraverseFilter[List].filterA(eo.expressions)(hasPredicate(_,lbl))
              tcsFiltered = maybeN match {
                case None => tcs
                case Some(n) => getElementAt(tcs, n)
              }
            } yield current.add(Value(tcsFiltered.map(TripleExprItem(_))))
            case IntTripleExprIndex(n) => {
              ok(current.add(getElementAt(eo.expressions, n).map(TripleExprItem(_))))
            }
            case _ => err(s"Matching TripleExprItem EachOf: unknown index ${index.show}") >>
                      ok(current)
          }
          case tc: TripleConstraint => index match {
            case LabelTripleExprIndex(lbl,maybeN) => for {
              check <- hasPredicate(tc,lbl)
              r <- if (check) maybeN match {
                case None | Some(1) => ok(current.add(tc))
                case Some(other) =>
                  err(s"Evaluate index tripleConstraint ${tc} with index ${index.show}. Value should be 1 and is ${other}") >>
                  ok(current)
              } else
                warning(s"evaluateIndex tripleConstraint ${tc} doesn't match ${index.show}. Predicate ${tc.predicate.show} != ${lbl.show}") >>
                ok(current)
            } yield r
            case IntTripleExprIndex(1) => tc.valueExpr match {
              case None =>
                warning(s"Matching triple constraint ${tc.show} with index 1 but no ShapeExpr: nothing to add") >>
                ok(current)
              case Some(se) =>
                ok(current.add(se))
            }
            case IntTripleExprIndex(other) =>
              err(s"Matching triple constraint ${tc.show} with int index: ${index.show} (it should be 1") >>
              ok(current)
            case _ =>
              err(s"Matching triple constraint ${tc.show} with unknown index: ${index.show}") >>
              ok(current)
          }
          case _ => err(s"Matching TripleExprItem: unknown: ${te.show}") >>
                    ok(current)
        }
        case _ => err(s"Unknown item: ${item.show}") >>
                  ok(current)
      }
    Foldable[List].foldM[Comp,Item,Value](items, zero)(cmb)
  }

  private def cmb(ctx: Context)(current: List[Item], item: Item): Comp[List[Item]] = for {
    next <- checkContext(ctx, item)
  } yield current ++ next


  private def evaluateStep(s: Schema)(current: Comp[Value], step: Step): Comp[Value] = step match {
    case es: ExprStep => {
      println(s"ExprStep: ${step.show}")
      es.maybeContext match {
        case None => for {
          currentValue <- current
          _ <- debug(s" Current value: ${currentValue.show}")
          value <- evaluateIndex(currentValue.items,es.exprIndex)
          _ <- debug(s" New value after evaluateIndex: ${value.show}")
        } yield value
        case Some(ctx) => for {
          currentValue <- current
          _ <- debug(s"Context step ${ctx.show}\nvalue: ${currentValue.show}")
          // (matched,unmatched) = currentValue.items.partition(checkContext(ctx))
          matched <- currentValue.items.foldM[Comp,List[Item]](List())(cmb(ctx))
          _ <- debug(s"Matched ${matched}")
          // _ <- debug(s"Unmatched ${unmatched}")
          newValue <- evaluateIndex(matched, es.exprIndex)
          _ <- debug(s"newValue ${newValue.show}")
          // errors: List[ProcessingError] = unmatched.map(UnmatchItemContextLabel(_,step,ctx))
          // _ <- errors.tell
        } yield newValue
      }
    }
    case ContextStep(ctx) => for {
     currentValue <- current
     matched <- currentValue.items.foldM[Comp,List[Item]](List())(cmb(ctx))
//     (matched,unmatched) = currentValue.items.partition(checkContext(ctx))
//     errors: List[ProcessingError] = unmatched.map(UnmatchItemContextLabel(_,step,ctx))
//     _ <- errors.tell
    } yield Value(matched) 
  }

  private def evaluateShapePath(p: ShapePath, s: Schema, v: Value): Comp[Value] = {
    val zero: Comp[Value] = if (p.startsWithRoot) {
      val v = Value(s.localShapes.map(ShapeExprItem(_)))
      println(s"Starts with root\nValue = ${v.show}")
      v.pure[Comp]
    } else v.pure[Comp]

    p.steps.foldLeft(zero)(evaluateStep(s))
  }

}

