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

  // Demande les mises à jour dès le démarrage et périodiquement toutes les 10 secondes
  override def preStart(): Unit = scheduleNextCheck()

  def receive: Receive = {
    case StatusUpdate(musiciansStatus, currentLeader) =>
    val statusArray = Array.fill(4)(-1) // Remplacez 4 par le nombre de musiciens
      musiciansStatus.foreach {
        case (id, "en vie") if currentLeader.contains(id) => statusArray(id) = 1 // Chef et en vie
        case (id, "en vie") => statusArray(id) = 0 // En vie mais pas chef
        case _ => // Les cas restants sont déjà initialisés à -1
      }
      println(s"Statut des musiciens : ${statusArray.mkString("[", ",", "]")}")
      scheduleNextCheck()
  }

  // Planifie le prochain check
  private def scheduleNextCheck(): Unit = {
    context.system.scheduler.scheduleOnce(10.seconds, parent, RequestStatusUpdate)
  }
}
