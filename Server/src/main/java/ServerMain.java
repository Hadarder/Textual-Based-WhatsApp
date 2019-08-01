import akka.actor.ActorSystem;
import akka.actor.Props;

public class ServerMain {
    public static void main(String[] args) {
        // Creating the system
        ActorSystem system = ActorSystem.create("ChatSystem");
        // Creating server manager
        system.actorOf(Props.create(Manager.class), "Manager");
    }
}
