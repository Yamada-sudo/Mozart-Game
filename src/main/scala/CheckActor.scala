package upmc.akka.leader

import akka.actor._
import scala.concurrent.duration._

case object PerformCheck
// Message demandé par CheckActor pour obtenir l'état actuel des musiciens
case object RequestStatusUpdate

// Message envoyé par Musicien à CheckActor avec les données nécessaires
case class StatusUpdate(musiciansStatus: Map[Int, String], currentLeader: Option[Int])

class CheckActor(parent: ActorRef) extends Actor {
  import context.dispatcher

    override def preStart(): Unit = {
    context.system.scheduler.schedule(0.seconds, 10.seconds, self, PerformCheck)
  }

  def receive: Receive = {
    case PerformCheck =>
      parent ! RequestStatusUpdate

    case StatusUpdate(musiciansStatus, currentLeader) =>
      val statusArray = Array.fill(4)(-1) // Assurez-vous que la taille correspond au nombre de musiciens
      musiciansStatus.foreach {
        case (id, "en vie") if currentLeader.contains(id) => statusArray(id) = 1 // Chef
        case (id, "en vie") => statusArray(id) = 0 // En vie
        case (id, "défaillant") => statusArray(id) = -1 // Défaillant
      }
      println(s"Statut des musiciens: ${statusArray.mkString("[", ", ", "]")}")
  }
}
