//> using toolkit latest
//> using file "../common/core.sc"
//> using file "../privategpt/privategpt.sc"

import os.{read => osRead, write => osWrite, *}
import core.*
import core.given
import util.*
import util.chaining.scalaUtilChainingOps
import scala.annotation.tailrec
import git.*
import poetry.*
import privategpt.*
import sttp.client4.quick.*
import sttp.client4.Response
import sttp.model.*
import upickle.default.*
import upickle.implicits.key
import java.time.LocalDateTime

// Focussing first in one shot queries but laying some foundation for multi turn, multi shard, multi agent conversations
// A conversation always involves a sender and a receiver, and we cannot assume if any is a human or a machine
// For extra flexibility, we will consider that a message is sent from a group of participants to another group of participants, as different individuals might assume different roles in a conversation

/**
 * Initial idea for how a conversation is structured just thinking about
 * property names. Types will be added later: Conversation( messages( Message(
 * Senders( Participant( address, role ) ), Receivers( Participant( address,
 * role ) ), localDateTime, content ) ) )
 */

enum Role:
  case User, Assistant, System

case class Participant(address: String, role: Role)

case class Message(
  senders: List[Participant],
  receivers: List[Participant],
  localDateTime: LocalDateTime,
  content: String,
)

case class Conversation(messages: List[Message])

// The participant address is some kind of identifier that can be used to identify the participant in a conversation. Can be an email, a phone number, an ftp address and so on.

// A conversation is naturally managed by the participants, which in the context of this project means the user and the assistant. A third expected participant is the system itself. The system is expected to be invisible to the user from an UI perspective but, besides piping the messages between the user and the assistant, it is expected to be able an active part of the conversation, so we keep in code what code is best for, leave the creative part to the user and the generative part to the assistant.

// The system will execute a sequence of actions with the objective of answering the user question, which might mean asking multiple questions to the assistant before going back to the user.

// A hidden figure in this context is the "specialist", in the context of specialist systems, so the abstractions in the system intend to allow an specialist to model the needed actions to mediate the interaction between the user and the assistant.

/**
 * Initial idea of how the actions are structured: ConversationManagement(
 * Actions( UserPrompt, SystemPrompt( rules ), AssistantResponse( rules ),
 * SystemResponse( rules ) ), startingAction followingActions followingAction )
 */

// Each action can potentially receive the output of the previous action as input, so we can model a sequence of actions that can be executed in a given order.
// The starting action is the first action to be executed and the following action is a function to return the action to be executed after a given action is executed.
// The followingActions is a dictionary where the key is the action and the value is all the actions that can be executed after the key action is executed.
// The rules will hold the logic to decide which action to execute next. If none is given, the default is to execute the first following action.
// We don't need a representation of a end state as we assume it is always returning the last output to the user.
// At first there will be no validation of cycles in the actions, so it is up to the user to avoid them.
// The idea is that the rules are as simple as possible, so they can be easily understood, maintained, extended and reused.

case class ActionContent[T](content: T)

sealed abstract class Rule[I, O](
  perform: (ActionContent[I]) => Option[ActionContent[O]],
  applies: (ActionContent[I]) => Boolean = (_: ActionContent[I]) => true,
  // TODO think about the real need of followingActions. Could be useful for rendering a diagram
  // followingActions: List[Action[I, O]],
):
  def apply(input: ActionContent[I]): Option[ActionContent[O]] =
    if applies(input) then perform(input) else None

case class UnitStringRule(
  perform: (ActionContent[Unit]) => Option[ActionContent[String]],
) extends Rule[Unit, String](perform)

sealed trait Action[I, O](rules: List[Rule[I, O]]):
  def apply(input: ActionContent[I]): Option[ActionContent[O]] =
    rules.foldLeft(None: Option[ActionContent[O]]) { (acc, rule) =>
      acc.orElse(rule(input))
    }

case class UserPrompt(
  rules: List[Rule[Unit, String]] = List(),
) extends Action[Unit, String](rules)

case class SystemPrompt(
  rules: List[Rule[String, String]],
) extends Action[String, String](rules)

case class AssistantResponse(
  rules: List[Rule[String, String]],
) extends Action[String, String](rules)

case class SystemResponse(
  rules: List[Rule[String, String]],
) extends Action[String, String](rules)

case class ConversationManagement(
  actions: List[Action[_, _]],
  startingAction: Action[_, _],
  followingActions: Map[Action[String, String], List[Action[String, String]]],
  currentAction: Action[_, _],
)
