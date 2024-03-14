import akka.actor._
import upmc.akka.ppc.DataBaseActor
import upmc.akka.ppc.DataBaseActor.Measure
import upmc.akka.ppc.DataBaseActor.GetMeasure
import upmc.akka.ppc.PlayerActor
import scala.concurrent.duration._
import akka.actor.{ActorSystem, Props}
import upmc.akka.ppc._
import scala.util.Random

class Conductor extends Actor {
  import context._


  // Créer les acteurs enfants dans Conductor
  val database: ActorRef = context.actorOf(Props[DataBaseActor], "database")
  val player: ActorRef = context.actorOf(Props[PlayerActor], "player")
  val provider: ActorRef = context.actorOf(Props[Provider], "provider")

  def receive: Receive = {
     case Conductor.StartGame =>
      println("Conductor: Game starts")
      val diceResult = rollDice()
      provider ! Provider.GetMeasure(diceResult)

    case measureNumber: Int =>  // Now expecting an Int from Provider
      println(s"Conductor: Received measure number $measureNumber from Provider")
      database ! DataBaseActor.GetMeasure(measureNumber)

    case measure: DataBaseActor.Measure =>
      println("Conductor: Received Measure from DataBaseActor, sending to Player")
      player ! measure
      system.scheduler.scheduleOnce(1800.milliseconds, self, Conductor.StartGame)
  }

  private def rollDice(): Int = {
    val die1 = scala.util.Random.nextInt(6) + 1
    val die2 = scala.util.Random.nextInt(6) + 1
    die1 + die2
  }
}

object Conductor {
  case object StartGame
}