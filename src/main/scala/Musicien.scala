package upmc.akka.leader
import upmc.akka.ppc.DataBaseActor
import upmc.akka.ppc.DataBaseActor.Measure
import upmc.akka.ppc.DataBaseActor.GetMeasure
import upmc.akka.ppc.PlayerActor._
import upmc.akka.ppc._
import akka.actor._
import scala.concurrent.duration._

case object Start
case class IAmTheLeader(musicianId: Int)
case object CheckMusicians
case class MusicianStatus(status: String, lastHeartbeat: Long)
case class PlayMusic(musicianId: Int)
case object StartGame
case class PlayMeasure(measure: DataBaseActor.Measure)
case class PlayNote(note: PlayerActor.MidiNote)
case class AnnounceAndPlay(musicianId: Int, note: PlayerActor.MidiNote)
case object RequestLeaderStatus 
class Musicien(val id: Int, val terminaux: List[Terminal]) extends Actor {
  import context.dispatcher

  val displayActor = context.actorOf(Props[DisplayActor], "displayActor")
  val heart = context.actorOf(Props(new HeartActor(self, terminaux, id)), "heart")
  val checkActor = context.actorOf(Props(new CheckActor(self)), "checkActor")

  val database: ActorRef = context.actorOf(Props[DataBaseActor], "database")
  val player: ActorRef = context.actorOf(Props[PlayerActor], "player")
  val provider: ActorRef = context.actorOf(Props[Provider], "provider")

  var musiciansStatus: Map[Int, MusicianStatus] = Map.empty
  var currentLeader: Option[Int] = None

  var schedulerCancellable: Option[Cancellable] = None
  var leaderAnnounceCancellable: Option[Cancellable] = None




  override def preStart(): Unit = {
    heart ! Start
    announcePresence()
    schedulePeriodicCheckMusicians()
    context.system.scheduler.scheduleOnce(10.seconds, self, CheckMusicians)
    requestLeaderStatus() 
  }

  def receive: Receive = {
    case Start =>
      handleStart()


    case Alive(musicianId) =>
      handleAliveSignal(musicianId)

    case RequestLeaderStatus =>
      if (currentLeader.contains(id)) {
        sender() ! IAmTheLeader(id) // Répondre si ce musicien est le leader
      }  

    case IAmTheLeader(leaderId) =>
      handleLeaderAnnouncement(leaderId)

    case CheckMusicians =>
      performCheckAndElectLeader()

    case StartGame if currentLeader.contains(id) => startGame()
   
    case measureNumber: Int => handleMeasureNumber(measureNumber)
   
    case measure: DataBaseActor.Measure => playMeasure(measure)
    
    case PlayNote(note) => player ! note

    case AnnounceAndPlay(musicianId, note) if musicianId == id =>
     self ! PlayNote(note) // Jouer la note
  
  }

  private def announcePresence(): Unit = {
    def stripQuotes(s: String): String = s.replace("\"", "")
    terminaux.filter(_.id != id).foreach { terminal =>
        // Nettoyer l'adresse IP pour enlever les guillemets
       val ipClean = stripQuotes(terminal.ip)
        // Créer la sélection d'acteur avec l'adresse IP nettoyée et le port
        val selection = context.actorSelection(s"akka.tcp://MozartSystem${terminal.id}@$ipClean:${terminal.port}/user/Musicien${terminal.id}")
          selection ! Alive(id)
          selection ! RequestLeaderStatus
    }
  }

  private def requestLeaderStatus(): Unit = {
    println("Qui est le leader?")
    def stripQuotes(s: String): String = s.replace("\"", "")
    terminaux.filter(_.id != id).foreach { terminal =>
      val ipClean = stripQuotes(terminal.ip)
      val selection = context.actorSelection(s"akka.tcp://MozartSystem${terminal.id}@$ipClean:${terminal.port}/user/Musicien${terminal.id}")
      selection ! RequestLeaderStatus // Demander le statut du leader aux autres musiciens
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
    sendStatusToCheckActor()
  }


  private def handleLeaderAnnouncement(leaderId: Int): Unit = {
    currentLeader = Some(leaderId)
    println(s"Le leader actuel est: Musicien $leaderId")
    sendStatusToCheckActor()
  }

 private def performCheckAndElectLeader(): Unit = {
    val currentTime = System.currentTimeMillis()
    musiciansStatus = musiciansStatus.filter {
      case (_, status) => (currentTime - status.lastHeartbeat) <= 15000
    }

    electLeaderIfNeeded()
    displayStatus()
  }

  private def schedulePeriodicCheckMusicians(): Unit = {
    context.system.scheduler.schedule(10.seconds, 6.seconds, self, CheckMusicians)
  }

  private def updateMusiciansStatus(): Unit = {
    val currentTime = System.currentTimeMillis()
    musiciansStatus = musiciansStatus.filter {
      case (_, status) => (currentTime - status.lastHeartbeat) <= 10000
    }
  }



private def electLeaderIfNeeded(): Unit = {
  val currentTime = System.currentTimeMillis()
  // Filtrer uniquement les musiciens en vie
  val aliveMusicians = musiciansStatus.filter {
    case (_, MusicianStatus(_, lastHeartbeat)) => (currentTime - lastHeartbeat) <= 15000
  }

  if (aliveMusicians.size < 2) {
    // S'il y a moins de deux musiciens en vie, aucun leader ne peut être élu ou maintenu
    currentLeader = None
    return // Sortie anticipée de la fonction
  }

  currentLeader match {
    case Some(leaderId) if aliveMusicians.contains(leaderId) =>
      // Le leader actuel est toujours en vie, aucune action requise
      println(s"Leader actuel ($leaderId) est toujours en vie. Aucun changement de leader.")

    case Some(leaderId) =>
      // Le leader actuel n'est plus en vie
      println(s"Leader actuel ($leaderId) n'est plus en vie. Élection d'un nouveau leader nécessaire.")
      electNewLeader(aliveMusicians)

    case None =>
      // Aucun leader actuellement, besoin d'élire un nouveau leader
      println("Aucun leader détecté. Élection d'un nouveau leader.")
      electNewLeader(aliveMusicians)
  }
}

private def electNewLeader(aliveMusicians: Map[Int, MusicianStatus]): Unit = {
  val potentialNewLeaderId = aliveMusicians.keys.min
  if (id == potentialNewLeaderId) {
    // Ce musicien a l'ID le plus bas parmi les musiciens en vie, il devient le nouveau leader
    currentLeader = Some(potentialNewLeaderId)
    announceLeader()
  } else {
    println(s"Musicien $id n'est pas le leader. Leader actuel: $potentialNewLeaderId")
  }
}


    private def announceLeader(): Unit = {

    if (id == currentLeader.get) {
    // Annoncer immédiatement une fois en tant que leader
    announceIAmTheLeaderNow()

    // Démarrer un scheduler pour annoncer périodiquement
    leaderAnnounceCancellable = Some(context.system.scheduler.schedule(0.seconds, 5.seconds)(announceIAmTheLeaderNow()))

    println(s"Je suis le nouveau leader: Musicien $id")
    schedulerCancellable.foreach(_.cancel())

    // Démarrer immédiatement le jeu
    self ! StartGame

    // Programmer le démarrage du jeu toutes les 18000 millisecondes
    schedulerCancellable = Some(
      context.system.scheduler.schedule(
        initialDelay = 1800.milliseconds, // Démarrer après 1800 ms pour la première fois
        interval = 1800.milliseconds, // Répéter toutes les 1800 ms
        receiver = self,
        message = StartGame
      )
    )
  }
    
  }

  private def announceIAmTheLeaderNow(): Unit = {
  terminaux.foreach { terminal =>
    if (terminal.id != id) {
      val ipClean = terminal.ip.replaceAll("\"", "") // Simplification de stripQuotes
      val selection = context.actorSelection(s"akka.tcp://MozartSystem${terminal.id}@$ipClean:${terminal.port}/user/Musicien${terminal.id}")
      selection ! IAmTheLeader(id)
    }
  }
  println(s"Je suis le chef d'orchestre : Musicien $id")
}

  private def stopLeaderAnnouncement(): Unit = {
  leaderAnnounceCancellable.foreach(_.cancel())
  }


  private def displayStatus(): Unit = {
    println(s"Leader actuel: ${currentLeader.getOrElse("Aucun")}")
  }


  private def sendStatusToCheckActor(): Unit = {
    val status = musiciansStatus.mapValues {
      case MusicianStatus("en vie", _) => "en vie"
      case _ => "défaillant"
    }
    checkActor ! StatusUpdate(status, currentLeader)
  }


private def chooseMusicianId(): Int = {
    val aliveNonLeaderMusicians = musiciansStatus.filterKeys(_ != id).collect {
      case (musicianId, MusicianStatus("en vie", _)) => musicianId
    }.toList

    if (aliveNonLeaderMusicians.nonEmpty) {
      aliveNonLeaderMusicians(scala.util.Random.nextInt(aliveNonLeaderMusicians.size))
    } else {
      -1
    }
  }

  override def postStop(): Unit = {
  schedulerCancellable.foreach(_.cancel())
  }

  private def rollDice(): Int = {
    val die1 = scala.util.Random.nextInt(6) + 1
    val die2 = scala.util.Random.nextInt(6) + 1
    die1 + die2
  }

  
private def startGame(): Unit = {
    // Implementation for starting the game
    println(s"Musicien $id (Leader): Starting game.")
    val diceResult = rollDice()
    provider ! Provider.GetMeasure(diceResult)
  }
  
  private def handleMeasureNumber(measureNumber: Int): Unit = {
    // Implementation for handling measure number
    println(s"Musicien $id (Leader): Received measure number $measureNumber from Provider.")
    database ! GetMeasure(measureNumber)
  }
  
  private def playMeasure(measure: DataBaseActor.Measure): Unit = {
  // Assure that the function is executed by the leader only
  if (currentLeader.contains(id)) {
    var musicianIdToPlay = chooseMusicianId()
    if(musicianIdToPlay != -1){
    println(s"Musicien choisi pour jouer la mesure : $musicianIdToPlay")

    measure.chords.foreach { chord =>
      chord.notes.foreach { note =>
        // Check if the selected musician is still available before sending the note
        if (!musiciansStatus.contains(musicianIdToPlay)) {
          // If the musician is not available, choose another one
          musicianIdToPlay = chooseMusicianId()
          while(chooseMusicianId() == -1 ){
            musicianIdToPlay = chooseMusicianId()
          }
          println(s"Changement de musicien en cours de mesure, nouveau musicien choisi : $musicianIdToPlay")
        }

        val midiNote = PlayerActor.MidiNote(note.pitch, note.vol, note.dur, chord.date)
        val selection = context.actorSelection(s"akka.tcp://MozartSystem${musicianIdToPlay}@127.0.0.1:600${musicianIdToPlay}/user/Musicien${musicianIdToPlay}")
        selection ! AnnounceAndPlay(musicianIdToPlay, midiNote)
      }
    }
  }else{
    println(" none musicien ready to play")
  }

  }
}

  

}
