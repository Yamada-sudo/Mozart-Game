package upmc.akka.leader

import akka.actor._

case class Message (content:String)
case class Alive(musicianId: Int)

class DisplayActor extends Actor {

     def receive = {

          case Message (content) => {
               println(content)
          }

     }
}
