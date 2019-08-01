import akka.actor.AbstractActor;
import akka.actor.ActorRef;
import akka.actor.ActorSelection;
import akka.pattern.Patterns;
import scala.concurrent.Await;
import scala.concurrent.Future;
import scala.concurrent.duration.Duration;

import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;

public class CommunicationActor extends AbstractActor {
    private ActorSelection manager; // Server manager
    private ActorRef ioActor; // IO actor to perform read and write operations
    private User user = null; // Current user
    private ActorRef inviter = null; // Inviter actor in group invite operations

    public CommunicationActor(ActorRef ioActor) {
        this.ioActor = ioActor;
    }

    public Receive createReceive() {
        return receiveBuilder()
                .match(PrintMessage.class, msg -> ioActor.tell(msg, self()))
                .match(Connect.class, this::handleConnectCommand)
                .match(Disconnect.class, this::handleDisconnectCommand)
                .match(UserCommand.class, this::handleUserCommand)
                .match(TextData.class, this::handleTextData)
                .match(FileData.class, this::handleFileData)
                .match(CreateGroup.class, this::handleCreateGroup)
                .match(LeaveGroup.class, this::handleLeaveGroup)
                .match(GroupText.class, this::handleGroupText)
                .match(GroupFile.class, this::handleGroupFile)
                .match(Inviter.class, this::handleInviter)
                .match(Invited.class, this::handleInvited)
                .match(InviteResponse.class, this::handleInviteResponse)
                .match(GroupRemove.class, this::handleGroupRemove)
                .match(CoAdminGroupCommand.class, this::handleCoAdminGroupCommand)
                .match(MuteCommand.class, this::handleMuteCommand)
                .build();
    }

    private void handleInviteResponse(InviteResponse response) {
        // If inviter exists and a response has been processes, send it to inviter and reset inviter
        if (user != null && inviter != null) {
            inviter.tell(response, self());
            inviter = null;
        }
    }

    private void handleConnectCommand(Connect cmd) {
        if (user != null) //User is already connected
            return;
        // Ask manager for response
        Future<Object> eventualResponse = Patterns.ask(manager, cmd, 1000);
        try {
            // Parse manager response
            Response response = (Response) Await.result(eventualResponse, Duration.apply(1000, TimeUnit.MILLISECONDS));
            // Handle success
            if (response instanceof Success) {
                user = cmd.getUser();
                // Print success message
                ioActor.tell(new PrintMessage(user.getUsername() + " has connected successfully!"), self());
                // Handle failure
            } else if (response instanceof Failure)
                // Print failure message
                ioActor.tell(new PrintMessage(cmd.getUser().getUsername() + " is in use!"), self());
        } catch (
                Exception e) {
            // Server has not responded
            ioActor.tell(new PrintMessage("server is offline!"), self());
        }

    }

    private void handleDisconnectCommand(Disconnect cmd) {
        if (user == null) // User is disconnected or something went wrong //User is disconnected
            return;
        cmd.setUsername(user.getUsername());
        // User wishes to disconnect, notify manager and wait for response
        Future<Object> eventualResponse = Patterns.ask(manager, cmd, 1000);
        try {
            Await.result(eventualResponse, Duration.apply(1000, TimeUnit.MILLISECONDS));
            // Print success message
            ioActor.tell(new PrintMessage(user.getUsername() + " has been disconnected successfully!"), self());
            user = null;

        } catch (Exception e) {
            // Server has not responded
            ioActor.tell(new PrintMessage("server is offline! try again later!"), self());
        }
    }

    private void handleUserCommand(UserCommand cmd) {
        if (user == null) // User is disconnected or something went wrong
            return;

        cmd.setSource(user.getUsername()); // Update command source username

        // Ask manager and wait for response
        Future<Object> eventualResponse = Patterns.ask(manager, cmd, 1000);
        try {
            Response response = (Response) Await.result(eventualResponse, Duration.apply(1000, TimeUnit.MILLISECONDS));
            // Handle success
            if (response instanceof Success) {
                // fetch user information from server response
                User targetUser = ((Success) response).getUser();

                if (cmd instanceof UserText)
                    // Send text message to target actor
                    targetUser.getCommunicationActor().tell(new TextData(cmd.getSource(), cmd.getTarget(), cmd.getData()), self());
                else if (cmd instanceof UserFile) {
                    // Data needed for sending file
                    FileData file = buildFileData(cmd.getSource(), cmd.getTarget(), cmd.getData());
                    if (file != null)
                        // Send file to target actor
                        targetUser.getCommunicationActor().tell(file, self());
                }
            } else {
                // Handle failure
                ioActor.tell(new PrintMessage(cmd.getTarget() + " does not exist!"), self());
            }
        } catch (Exception e) {
            // Server has not responded
            ioActor.tell(new PrintMessage("server is offline! try again later!"), self());
        }

    }

    private FileData buildFileData(String source, String target, String path) {
        try {
            // Read and save file content in binary mode
            byte[] fileContent = Files.readAllBytes(Paths.get(path));
            // Fetch file name
            String[] splitPath = path.split("/");
            String filename = splitPath[splitPath.length - 1];
            return new FileData(source, target, fileContent, filename);
        } catch (Exception e) {
            // File does not exist
            ioActor.tell(new PrintMessage(path + " does not exist!"), self());
            return null;
        }
    }

    private void handleTextData(TextData data) {
        if (user == null) // User is disconnected or something went wrong
            return;

        // Get current time
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("HH:mm:ss");
        LocalDateTime now = LocalDateTime.now();
        // Present data in desired format
        String message = "[" + dtf.format(now) + "]"
                + "[" + data.getTarget() + "][" + data.getSource() + "] "
                + data.getData();
        // Print message by IO actor
        ioActor.tell(new PrintMessage(message), self());
    }

    private void handleFileData(FileData data) {
        if (user == null) // User is disconnected or something went wrong
            return;

        // Get local path to save file
        String path = Paths.get("").toAbsolutePath().toString() + "/" + data.getFilename();
        try {
            // Write file content
            Files.write(Paths.get(path), data.getData());
        } catch (Exception e) {
            System.out.println("Error in saving file\n");
        }

        // Get current time
        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("HH:mm:ss");
        LocalDateTime now = LocalDateTime.now();
        // Present data in desired format
        String message = "[" + dtf.format(now) + "]"
                + "[" + data.getTarget() + "][" + data.getSource() + "] File received: " + path;
        // Print message by IO actor
        ioActor.tell(new PrintMessage(message), self());
    }

    private void handleCreateGroup(CreateGroup cmd) {
        if (user == null) // User is disconnected or something went wrong
            return;

        cmd.setCreator(user.getUsername());
        // Wait for server response
        Future<Object> eventualResponse = Patterns.ask(manager, cmd, 1000);
        try {
            Response response = (Response) Await.result(eventualResponse, Duration.apply(1000, TimeUnit.MILLISECONDS));
            // Handle success
            if (response instanceof Success)
                ioActor.tell(new PrintMessage(cmd.getGroupname() + " created successfully!"), self());
                // Handle failure
            else if (response instanceof Failure)
                ioActor.tell(new PrintMessage(cmd.getGroupname() + " already exists!"), self());

        } catch (Exception e) {
            // Server has not responded
            ioActor.tell(new PrintMessage("server is offline! try again later!"), self());
        }
    }

    private void handleLeaveGroup(LeaveGroup cmd) {
        if (user == null) // User is disconnected or something went wrong
            return;
        cmd.setUsername(user.getUsername()); // Set command source username

        // Wait for server response
        Future<Object> eventualResponse = Patterns.ask(manager, cmd, 1000);
        try {
            Response response = (Response) Await.result(eventualResponse, Duration.apply(1000, TimeUnit.MILLISECONDS));
            // Handle failure
            if (response instanceof Failure) {
                Failure failure = (Failure) response;
                switch (failure.getReason()) {
                    // Group does not exist
                    case GROUPNAME:
                        ioActor.tell(new PrintMessage(cmd.getGroupname() + " does not exist!"), self());
                        break;
                    // Source username is not in group
                    case SOURCE:
                        ioActor.tell(new PrintMessage(cmd.getUsername() + " is not in " + cmd.getGroupname() + "!"), self());
                        break;
                }
            }

        } catch (Exception e) {
            // Server has not responded
            ioActor.tell(new PrintMessage("server is offline! try again later!"), self());
        }
    }

    private void handleGroupText(GroupText cmd) {
        if (user == null) // User is disconnected or something went wrong
            return;
        cmd.setSource(user.getUsername()); // Update source username
        // build command for sending data
        TextData data = new TextData(cmd.getSource(), cmd.getGroupname(), cmd.getMessage());
        sendGroupData(data);
    }

    private void handleGroupFile(GroupFile cmd) {
        if (user == null) // User is disconnected or something went wrong
            return;
        cmd.setSource(user.getUsername()); // Update source username
        // Data needed for sending file
        FileData file = buildFileData(cmd.getSource(), cmd.getGroupname(), cmd.getFilename());
        if (file != null)
            sendGroupData(file);
    }

    private void sendGroupData(Data cmd) {
        // Send data to server for broadcast and wait for response
        Future<Object> eventualResponse = Patterns.ask(manager, cmd, 1000);
        try {
            Response response = (Response) Await.result(eventualResponse, Duration.apply(1000, TimeUnit.MILLISECONDS));
            if (response instanceof Failure) { // Handle failure
                Failure failure = (Failure) response;
                switch (failure.getReason()) {
                    // group does not exist
                    case GROUPNAME:
                        ioActor.tell(new PrintMessage(cmd.getTarget() + " does not exist!"), self());
                        break;
                    // source username is not in group
                    case SOURCE:
                        ioActor.tell(new PrintMessage("You are not part of " + cmd.getTarget() + "!"), self());
                        break;
                    // source username is muted and therefore cannot send messages
                    case MUTED:
                        ioActor.tell(new PrintMessage("You are muted for " + failure.getData() + " milliseconds in " + cmd.getTarget() + "!"), self());
                        break;
                }
            }
        } catch (Exception e) {
            // Server has not responded
            ioActor.tell(new PrintMessage("server is offline! try again later!"), self());
        }
    }

    private void handleInviter(Inviter cmd) {
        if (user == null) // User is disconnected or something went wrong
            return;
        cmd.setSource(user.getUsername()); // Update source username

        // Wait for user information from server
        Future<Object> eventualResponse = Patterns.ask(manager, cmd, 1000);
        try {
            Response response = (Response) Await.result(eventualResponse, Duration.apply(1000, TimeUnit.MILLISECONDS));
            if (response instanceof Success) { // Handle success
                User targetUser = ((Success) response).getUser();

                // Build invite message and send it to target user, wait for response
                Invited invited = new Invited(cmd.getGroupname(), cmd.getSource(), cmd.getTarget());
                Future<Object> targetEventualResponse = Patterns.ask(targetUser.getCommunicationActor(), invited, 20000);
                try {
                    Response targetResponse = (Response) Await.result(targetEventualResponse, Duration.apply(20000, TimeUnit.MILLISECONDS));
                    if (targetResponse instanceof Confirm) { // Handle confirmation
                        // Notify manager to add target to group
                        manager.tell(new GroupAdd(cmd.getGroupname(), cmd.getSource(), cmd.getTarget()), self());
                        // Notify target about addition
                        targetUser.getCommunicationActor().tell(new TextData(cmd.getSource(), cmd.getGroupname(), "Welcome to " + cmd.getGroupname() + "!"), self());
                    }
                    // Otherwise, no action is needed

                } catch (Exception e) {
                } // User has not responded in time, considering this as decline

            } else if (response instanceof Failure) { // Handle failure from server
                Failure failure = (Failure) response;
                switch (failure.getReason()) {
                    // Group does not exist
                    case GROUPNAME:
                        ioActor.tell(new PrintMessage(cmd.getTarget() + " does not exist!"), self());
                        break;
                        // Source user does not have privileges
                    case SOURCE:
                        ioActor.tell(new PrintMessage("You are neither an admin nor a co-admin of " + cmd.getGroupname() + "!"), self());
                        break;
                        // Target user does not exist
                    case TARGET:
                        ioActor.tell(new PrintMessage(cmd.getTarget() + " does not exist!"), self());
                        break;
                        // Target user is already in group
                    case INGROUP:
                        ioActor.tell(new PrintMessage(cmd.getTarget() + " is already in " + cmd.getGroupname() + "!"), self());
                        break;
                }
            }
        } catch (Exception e) {
            // Server has not responded
            ioActor.tell(new PrintMessage("server is offline! try again later!"), self());
        }
    }

    private void handleInvited(GroupInvite cmd) {
        inviter = sender();
        // Send invite message for printing to user
        handleTextData(new TextData(cmd.getSource(), cmd.getGroupname(), "You have been invited to " + cmd.getGroupname() + ", Accept?"));
    }

    private void handleGroupRemove(GroupRemove cmd) {
        if (user == null) // User is disconnected or something went wrong
            return;
        cmd.setSource(user.getUsername()); // Update source username

        // Wait for server response
        Future<Object> eventualResponse = Patterns.ask(manager, cmd, 1000);
        try {
            Response response = (Response) Await.result(eventualResponse, Duration.apply(1000, TimeUnit.MILLISECONDS));
            if (response instanceof Success) { // Handle success
                // Fetch user information from server response and notify him about removal
                User targetUser = ((Success) response).getUser();
                targetUser.getCommunicationActor().tell(new TextData(cmd.getSource(), cmd.getGroupname(), "You have been removed from " + cmd.getGroupname() + " by " + cmd.getSource() + "!"), self());
            } else if (response instanceof Failure) { // Handle failure
                Failure failure = (Failure) response;
                handleFailure(failure, cmd.getGroupname(), cmd.getTarget());
            }
        } catch (Exception e) {
            // Server has not responded
            ioActor.tell(new PrintMessage("server is offline! try again later!"), self());
        }
    }

    private void handleCoAdminGroupCommand(CoAdminGroupCommand cmd) {
        if (user == null) // User is disconnected or something went wrong
            return;
        cmd.setSource(user.getUsername()); // Update source username

        // Wait for server response
        Future<Object> eventualResponse = Patterns.ask(manager, cmd, 1000);
        try {
            Response response = (Response) Await.result(eventualResponse, Duration.apply(1000, TimeUnit.MILLISECONDS));
            if (response instanceof Success) { // Handle success
                User targetUser = ((Success) response).getUser();
                if (cmd instanceof CoAdminAdd) // Notify target about promotion to co admin
                    targetUser.getCommunicationActor().tell(new TextData(cmd.getSource(), cmd.getGroupname(), "You have been promoted to co-admin in " + cmd.getGroupname() + "!"), self());
                else if (cmd instanceof CoAdminRemove) // Notify target about demotion to user
                    targetUser.getCommunicationActor().tell(new TextData(cmd.getSource(), cmd.getGroupname(), "You have been demoted to user in " + cmd.getGroupname() + "!"), self());

            } else if (response instanceof Failure) { // Handle failure
                Failure failure = (Failure) response;
                handleFailure(failure, cmd.getGroupname(), cmd.getTarget());
            }
        } catch (Exception e) {
            // Server has not responded
            ioActor.tell(new PrintMessage("server is offline! try again later!"), self());
        }
    }

    private void handleMuteCommand(MuteCommand cmd) {
        if (user == null) // User is disconnected or something went wrong
            return;
        cmd.setSource(user.getUsername()); // Update source username

        // Wait for server response
        Future<Object> eventualResponse = Patterns.ask(manager, cmd, 1000);
        try {
            Response response = (Response) Await.result(eventualResponse, Duration.apply(1000, TimeUnit.MILLISECONDS));
            if (response instanceof Success) { // Handle success
                User targetUser = ((Success) response).getUser();
                if (cmd instanceof MuteMember) // Notify target about mute
                    targetUser.getCommunicationActor().tell(new TextData(cmd.getSource(), cmd.getTarget(), "You have been muted for " + ((MuteMember) cmd).getDuration() + " in " + cmd.getGroupname() + " by " + cmd.getSource() + "!"), self());
                else if (cmd instanceof UnmuteMember) // Notify target about unmute
                    targetUser.getCommunicationActor().tell(new TextData(cmd.getSource(), cmd.getTarget(), "You have been unmuted in " + cmd.getGroupname() + " by " + cmd.getSource() + "!"), self());

            } else if (response instanceof Failure) { //Handle failure
                Failure failure = (Failure) response;
                handleFailure(failure, cmd.getGroupname(), cmd.getTarget());
            }

        } catch (Exception e) {
            // Server has not responded
            ioActor.tell(new PrintMessage("server is offline! try again later!"), self());
        }
    }

    private void handleFailure(Failure failure, String groupname, String target) {
        switch (failure.getReason()) {
            case GROUPNAME: // Group does not exist
                ioActor.tell(new PrintMessage(groupname + " does not exist!"), self());
                break;
            case SOURCE: // Source user does not have privileges
                ioActor.tell(new PrintMessage("You are neither an admin nor a co-admin of " + groupname + "!"), self());
                break;
            case TARGET: // Target user does not exist
                ioActor.tell(new PrintMessage(target + " does not exist!"), self());
                break;
            case NOTINGROUP: // Target user is not a member of the group
                ioActor.tell(new PrintMessage(target + " is not a member of " + groupname + "!"), self());
                break;
            case NOTMUTED: // Target user is not muted (and needs to be)
                ioActor.tell(new PrintMessage(target + " is not muted!"), self());
                break;
            case FORBIDDEN: // Forbidden operation over group admin
                ioActor.tell(new PrintMessage(target + " is the admin of the group!"), self());
                break;
        }
    }

    public void preStart() {
        // Connect to server manager, this is the first step once the Actor is added to the system
        manager = getContext().actorSelection(
                "akka.tcp://ChatSystem@127.0.0.1:3553/user/Manager");
    }
}
