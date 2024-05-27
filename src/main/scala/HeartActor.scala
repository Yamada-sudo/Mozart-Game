package upmc.akka.leader

import akka.actor._
import scala.concurrent.duration._

// Assurez-vous que le message Alive est correctement défini ailleurs dans votre projet
case object SendHeartbeat

class HeartActor(parentActor: ActorRef, terminaux: List[Terminal], musicianId: Int) extends Actor {
  import context.dispatcher
  override def preStart(): Unit = {
    sendHeartbeat() // Commence la première séquence d'envoi de battement de coeur
  }

  // Méthode récursive pour envoyer des battements de coeur
  def sendHeartbeat(): Unit = {
    def stripQuotes(s: String): String = s.replace("\"", "")
    terminaux.foreach { terminal =>
      if (terminal.id != musicianId) {
      // Nettoyer l'adresse IP pour enlever les guillemets
      val ipClean = stripQuotes(terminal.ip)
      // Créer la sélection d'acteur avec l'adresse IP nettoyée et le port
      val selection = context.actorSelection(s"akka.tcp://MozartSystem${terminal.id}@$ipClean:${terminal.port}/user/Musicien${terminal.id}")
    
      // Envoyer le message Alive à l'acteur sélectionné
      selection ! Alive(musicianId)
    }
  }

    parentActor ! Alive(musicianId)

    // Planifie le prochain battement de coeur
    context.system.scheduler.scheduleOnce(5.seconds) {
      sendHeartbeat()
    }
  }

  def receive: Receive = {
    case SendHeartbeat =>
      sendHeartbeat()
  }
}
