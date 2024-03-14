package upmc.akka.leader

import akka.actor._
import scala.concurrent.duration._

case object Start
case class IAmTheLeader(musicianId: Int)
case object CheckMusicians
case class MusicianStatus(status: String, lastHeartbeat: Long)

class Musicien(val id: Int, val terminaux: List[Terminal]) extends Actor {
  import context.dispatcher

  val displayActor = context.actorOf(Props[DisplayActor], "displayActor")
  val heart = context.actorOf(Props(new HeartActor(self, terminaux, id)), "heart")

  var musiciansStatus: Map[Int, MusicianStatus] = Map.empty
  var currentLeader: Option[Int] = None

  override def preStart(): Unit = {
    heart ! Start
    announcePresence()
    schedulePeriodicCheckMusicians()
    self ! CheckMusicians
  }

  def receive: Receive = {
    case Start =>
      handleStart()

    case Alive(musicianId) =>
      handleAliveSignal(musicianId)

    case IAmTheLeader(leaderId) =>
      handleLeaderAnnouncement(leaderId)

    case CheckMusicians =>
      performCheckAndElectLeader()
  }

  private def announcePresence(): Unit = {
    def stripQuotes(s: String): String = s.replace("\"", "")
    terminaux.filter(_.id != id).foreach { terminal =>
        // Nettoyer l'adresse IP pour enlever les guillemets
       val ipClean = stripQuotes(terminal.ip)
        // Créer la sélection d'acteur avec l'adresse IP nettoyée et le port
        val selection = context.actorSelection(s"akka.tcp://MozartSystem${terminal.id}@$ipClean:${terminal.port}/user/Musicien${terminal.id}")
          selection ! Alive(id)
    }
  }

  private def handleStart(): Unit = {
    displayActor ! Message(s"Musicien $id is created")
  }

  private def handleAliveSignal(musicianId: Int): Unit = {
  musiciansStatus = musiciansStatus.updated(musicianId, MusicianStatus("en vie", System.currentTimeMillis()))
  if (musicianId != id) {
    // Send leader information if it exists and the signal is not from yourself
    currentLeader match {
      case Some(leaderId) if leaderId != id => sender() ! IAmTheLeader(leaderId)
      case None => // No leader known, wait for the next Alive signal
      case _ => // Ignore if you are the leader
    }
    } 
  }


  private def handleLeaderAnnouncement(leaderId: Int): Unit = {
    currentLeader = Some(leaderId)
    println(s"Le leader actuel est: Musicien $leaderId")
  }

 private def performCheckAndElectLeader(): Unit = {
    val currentTime = System.currentTimeMillis()
    musiciansStatus = musiciansStatus.filter {
      case (_, status) => (currentTime - status.lastHeartbeat) <= 11000
    }

    electLeaderIfNeeded()
    displayStatus()
  }

  private def schedulePeriodicCheckMusicians(): Unit = {
    context.system.scheduler.schedule(0.seconds, 10.seconds, self, CheckMusicians)
  }

  private def updateMusiciansStatus(): Unit = {
    val currentTime = System.currentTimeMillis()
    musiciansStatus = musiciansStatus.filter {
      case (_, status) => (currentTime - status.lastHeartbeat) <= 11000
    }
  }

  private def electLeaderIfNeeded(): Unit = {
  val currentTime = System.currentTimeMillis()
  val aliveMusicians = musiciansStatus.filter {
    case (_, MusicianStatus(_, lastHeartbeat)) => (currentTime - lastHeartbeat) <= 11000
  }

  if (aliveMusicians.size >= 2 && (currentLeader.isEmpty || !aliveMusicians.keySet.contains(currentLeader.get))) {
    val potentialNewLeaderId = aliveMusicians.keys.min
    // If you are the new potential leader and no current leader is present or the current leader is not known to be alive
    if (id == potentialNewLeaderId && (currentLeader.isEmpty || !aliveMusicians.keySet.contains(currentLeader.get))) {
      currentLeader = Some(potentialNewLeaderId)
      announceLeader()
    }
  } else if (aliveMusicians.isEmpty || aliveMusicians.size < 2) {
    // No leader if not enough musicians
    currentLeader = None
  }
}


    private def announceLeader(): Unit = {
    println(s"Je suis le nouveau leader: Musicien $id")
    def stripQuotes(s: String): String = s.replace("\"", "")
    terminaux.foreach { terminal =>
      if (terminal.id != id) {
      // Nettoyer l'adresse IP pour enlever les guillemets
        val ipClean = stripQuotes(terminal.ip)
        // Créer la sélection d'acteur avec l'adresse IP nettoyée et le port
        val selection = context.actorSelection(s"akka.tcp://MozartSystem${terminal.id}@$ipClean:${terminal.port}/user/Musicien${terminal.id}")
        println("selected "+selection)
        selection ! IAmTheLeader(id)
      }
    }
  }

  private def displayStatus(): Unit = {
    musiciansStatus.foreach { case (musicianId, MusicianStatus(status, _)) =>
      println(s"Musicien $musicianId est $status ${if(currentLeader.contains(musicianId)) "(chef)" else ""}")
    }
    println(s"Leader actuel: ${currentLeader.getOrElse("Aucun")}")
  }
}
