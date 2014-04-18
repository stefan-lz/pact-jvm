package au.com.dius.pact.consumer

import au.com.dius.pact.model._
import au.com.dius.pact.model.Pact.ConflictingInteractions
import scalaz.{Validation, Success, Failure}
import scala.util.Try

object PactVerification {
  type VerificationResult = Validation[Seq[FailureMode], Unit]
  trait FailureMode

  implicit def failure(f: FailureMode) = Failure[Seq[FailureMode], Unit](Seq(f))

  private def logEach(msg: String, iterable: Iterable[AnyRef]): String = {
    s"$msg:${iterable.map("\n" + _)}"
  }

  case class MissingInteractions(missing: Iterable[Interaction]) extends FailureMode {
    override def toString: String = {
      logEach("missing interactions:", missing)
    }
  }

  case class UnexpectedInteractions(unexpected: Iterable[Interaction]) extends FailureMode {
    override def toString: String = {
      logEach("unexpected interactions:", unexpected)
    }
  }

  case class ConsumerTestsFailed(error: Throwable) extends FailureMode

  case class PactMergeFailed(error: ConflictingInteractions) extends FailureMode {
    override def toString: String = {
      s"This interaction conflicts with others: \n$error"
    }
  }

  case class FailedToWritePact(e: Throwable) extends FailureMode {
    override def toString: String = {
      s"Failed to write pact: ${e.getMessage} stackTrace:\n${e.getStackTraceString}"
    }
  }

  def apply(expected: Iterable[Interaction], actual: Iterable[Interaction]): VerificationResult = {
    val invalidResponse = Response(500, None, None)
    //TODO: work out the correct way to combine these validations
    (allExpectedInteractions(expected, actual), noUnexpectedInteractions(invalidResponse, actual)) match {
      case (Failure(a), Failure(b)) => Failure(a ++ b)
      case (Failure(a), _ ) => Failure(a)
      case (_, Failure(b)) => Failure(b)
      case (_, _) => Success()
    }
  }

  def apply(expected: Iterable[Interaction], actual: Iterable[Interaction], testResult: Try[Unit]): VerificationResult = {
    testResult match {
      case scala.util.Success(_) => PactVerification(expected, actual)
      case scala.util.Failure(t) => ConsumerTestsFailed(t)
    }
  }

  def noUnexpectedInteractions(invalid: Response, actual: Iterable[Interaction]): VerificationResult = {
    val unexpected = actual.filter(_.response == invalid)
    if(unexpected.isEmpty) {
      Success()
    } else {
      UnexpectedInteractions(unexpected)
    }
  }

  def allExpectedInteractions(expected: Iterable[Interaction], actual: Iterable[Interaction]): VerificationResult = {
    def in(f: Iterable[Interaction])(i:Interaction): Boolean = {
      RequestMatching(f, true).findResponse(i.request).isDefined
    }
    val missing = expected.filterNot(in(actual))
    if(missing.isEmpty) {
      Success()
    } else {
      MissingInteractions(missing)
    }
  }

}
