import akka.actor.ActorRef;

import java.io.Serializable;

public class User implements Serializable {
    private String username;
    private ActorRef communicationActor;

    public User(String username, ActorRef communicationActor) {
        this.username = username;
        this.communicationActor = communicationActor;
    }

    public String getUsername() {
        return username;
    }

    public ActorRef getCommunicationActor() {
        return communicationActor;
    }
}