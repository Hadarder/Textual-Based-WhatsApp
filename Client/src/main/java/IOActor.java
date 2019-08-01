import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.Props;

import java.util.Arrays;

public class IOActor extends AbstractActor {
    ActorRef communicationActor;

    @Override
    public Receive createReceive() {
        return receiveBuilder()
                .match(ProcessMessage.class, msg -> buildCommand(msg.getMessage()))
                .match(PrintMessage.class, msg -> System.out.println(msg.getMessage()))
                .build();
    }

    public void preStart() {
        // Create communication actor to communicate with the server
        communicationActor = getContext().getSystem().actorOf(Props.create(CommunicationActor.class, self()), "CommunicationActor");
    }

    private void buildCommand(String message) {
        // Split message by spaces to match the input
        String[] splitMessage = message.split(" ");
        // Process user response to a group invite
        if (splitMessage.length == 1)
            switch (splitMessage[0].toLowerCase()) {
                case "yes":
                    // Build confirm message
                    communicationActor.tell(new Confirm(), self());
                    break;
                case "no":
                    // Build decline message
                    communicationActor.tell(new Decline(), self());
                    break;
            }

        // Other short messages are invalid
        if (splitMessage.length < 2)
            return;

        // Other user operations
        switch (splitMessage[0]) {
            case "/user":
                buildUserCommand(splitMessage);
                break;
            case "/group":
                buildGroupCommand(splitMessage);
                break;
        }
    }

    // Build user operations commands
    private void buildUserCommand(String[] message) {
        Command cmd = null;
        switch (message[1]) {
            case "connect":
                if (message.length != 3)
                    return;
                // Build connect command for user
                User user = new User(message[2], communicationActor);
                cmd = new Connect(user);
                break;

            case "disconnect":
                if (message.length != 2)
                    return;
                // Build disconnect command for user
                cmd = new Disconnect(null);
                break;

            case "text":
                if (message.length < 4)
                    return;
                // Pass entire text including spaces
                String[] splitText = Arrays.copyOfRange(message, 3, message.length);
                String text = String.join(" ", splitText);
                // Build text command for user
                cmd = new UserText(null, message[2], text);
                break;
            case "file":
                if (message.length != 4)
                    return;
                // Build file command for user
                cmd = new UserFile(null, message[2], message[3]);
                break;
        }

        if (cmd != null)
            // Pass command to communication actor to continue processing
            communicationActor.tell(cmd, self());
    }

    // Build group operations commands
    private void buildGroupCommand(String[] message) {
        Command cmd = null;
        switch (message[1]) {
            case "create":
                if (message.length != 3)
                    return;
                // Build group creation command
                cmd = new CreateGroup(message[2], null);
                break;

            case "leave":
                if (message.length != 3)
                    return;
                // Build group leave command
                cmd = new LeaveGroup(message[2], null);
                break;

            case "send":
                if (message.length < 5)
                    return;
                switch (message[2]) {
                    case "text":
                        // Pass entire text including spaces
                        String[] splitText = Arrays.copyOfRange(message, 4, message.length);
                        String text = String.join(" ", splitText);
                        // Build group text command
                        cmd = new GroupText(message[3], null, text);
                        break;
                    case "file":
                        if (message.length != 5)
                            return;
                        // Build group file command
                        cmd = new GroupFile(message[3], null, message[4]);
                        break;
                }
                break;

            //User operations inside group
            case "user":
                if (message.length < 5)
                    return;
                switch (message[2]) {
                    case "invite":
                        if (message.length != 5)
                            return;
                        // Build group invite command
                        cmd = new Inviter(message[3], null, message[4]);
                        break;
                    case "remove":
                        if (message.length != 5)
                            return;
                        // Build group remove command
                        cmd = new GroupRemove(message[3], null, message[4]);
                        break;
                    case "mute":
                        if (message.length != 6)
                            return;
                        try {
                            // Parse duration to Long
                            Long duration = Long.parseLong(message[5]);
                            // Build group mute command
                            cmd = new MuteMember(message[3], null, message[4], duration);
                        } catch (Exception e) {
                            System.out.println("<timeinseconds> must be of type long!");
                        }
                        break;
                    case "unmute":
                        if (message.length != 5)
                            return;
                        // Build group unmute command
                        cmd = new UnmuteMember(message[3], null, message[4]);
                        break;
                }
                break;

            case "coadmin":
                if (message.length != 5)
                    return;
                switch (message[2]) {
                    case "add":
                        // Build group add coadmin command
                        cmd = new CoAdminAdd(message[3], null, message[4]);
                        break;
                    case "remove":
                        // Build group remove coadmin command
                        cmd = new CoAdminRemove(message[3], null, message[4]);
                        break;
                }
                break;
        }

        // Pass command to communication actor to continue processing
        if (cmd != null)
            communicationActor.tell(cmd, self());
    }
}
