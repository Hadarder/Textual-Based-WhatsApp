import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;

import java.util.Scanner;


public class ClientMain {
    static Scanner scanner = new Scanner(System.in);
    static ActorRef ioActor;

    public static void main(String[] args) {
        // Creating the system
        ActorSystem system = ActorSystem.create("Client");
        // Creating system io actor
        ioActor = system.actorOf(Props.create(IOActor.class), "IOActor");

        readAndFire();
    }

    private static void readAndFire() {
        String message;

        // Read user input and pass it to io actor
        while (true) {
            message = scanner.nextLine();
            ioActor.tell(new ProcessMessage(message), ActorRef.noSender());
        }
    }
}