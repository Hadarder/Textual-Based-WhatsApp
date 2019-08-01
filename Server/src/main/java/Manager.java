import akka.actor.AbstractActor;
import akka.actor.Cancellable;
import akka.actor.Scheduler;
import akka.routing.ActorRefRoutee;
import akka.routing.BroadcastRoutingLogic;
import akka.routing.Routee;
import akka.routing.Router;
import scala.concurrent.duration.FiniteDuration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class Manager extends AbstractActor {
    // Map of users who connected to the server
    private HashMap<String, User> onlineUsers = new HashMap<>();
    // Map of all the groups in the system
    private HashMap<String, Group> groups = new HashMap<>();
    // System scheduler object
    private Scheduler scheduler = context().system().scheduler();

    @Override
    public Receive createReceive() {
        return receiveBuilder().match(Connect.class, this::handleConnect)
                .match(Disconnect.class, this::handleDisconnect)
                .match(UserCommand.class, this::handleUserCommand)
                .match(CreateGroup.class, this::handleCreateGroup)
                .match(LeaveGroup.class, this::handleLeaveGroup)
                .match(Data.class, this::handleGroupData)
                .match(Inviter.class, this::handleInviter)
                .match(GroupAdd.class, this::handleGroupAdd)
                .match(GroupRemove.class, this::handleGroupRemove)
                .match(CoAdminGroupCommand.class, this::handleCoAdminGroupCommand)
                .match(MuteMember.class, this::handleMuteMember)
                .match(UnmuteMember.class, this::handleUnmuteMember)
                .build();
    }

    // Verify group exists
    private boolean validateGroupExists(String groupname) {
        if (!groups.containsKey(groupname)) {
            sender().tell(new Failure(Reason.GROUPNAME, null), self());
            return false;
        }
        return true;
    }

    // Verify target user exists
    private boolean validateTargetExists(String target) {
        if (!onlineUsers.containsKey(target)) {
            sender().tell(new Failure(Reason.TARGET, null), self());
            return false;
        }
        return true;
    }

    // Verify source exists and have required privileges
    private boolean validateSourcePrivileges(Group group, String source) {
        if (!group.isMember(source) ||
                !(group.isMemberState(source, State.ADMIN) || group.isMemberState(source, State.COADMIN))) {
            sender().tell(new Failure(Reason.SOURCE, null), self());
            return false;
        }
        return true;
    }

    // Verify user is a member of group
    private boolean validateMembership(Group group, String username) {
        if (!group.isMember(username)) {
            sender().tell(new Failure(Reason.NOTINGROUP, null), self());
            return false;
        }
        return true;
    }

    // Verify user is not a member of group
    private boolean validateNonMembership(Group group, String username) {
        if (group.isMember(username)) {
            sender().tell(new Failure(Reason.INGROUP, null), self());
            return false;
        }
        return true;
    }

    // Verify user is not an admin of a group
    private boolean validateAdminOperations(Group group, String username) {
        if (group.isMemberState(username, State.ADMIN)) {
            sender().tell(new Failure(Reason.FORBIDDEN, null), self());
            return false;
        }
        return true;
    }

    // Verify user is muted in group
    private boolean validateMuted(Group group, String username) {
        if (!group.isMemberState(username, State.MUTE)) {
            sender().tell(new Failure(Reason.NOTMUTED, null), self());
            return false;
        }
        return true;
    }

    // Verify source is a member of the group
    private boolean validateSourceMembership(Group group, String source) {
        if (!group.isMember(source)) {
            sender().tell(new Failure(Reason.SOURCE, null), self());
            return false;
        }
        return true;
    }

    // Validations used frequently
    private boolean commonValidations(Group group, String groupname, String source, String target) {
        return (validateGroupExists(groupname) &&
                validateTargetExists(target) &&
                validateSourcePrivileges(group, source) &&
                validateMembership(group, target) &&
                validateAdminOperations(group, target));
    }

    private void handleUnmuteMember(UnmuteMember cmd) {
        // Validations
        Group group = groups.get(cmd.getGroupname());

        if (!commonValidations(group, cmd.getGroupname(), cmd.getSource(), cmd.getTarget()) &&
                validateMuted(group, cmd.getTarget()))
            return;

        // Unmute target user in group
        group.unmuteMember(cmd.getTarget());
        // Notify sender about success
        sender().tell(new Success(onlineUsers.get(cmd.getTarget())), self());
    }

    private void handleMuteMember(MuteMember cmd) {
        // Validations
        Group group = groups.get(cmd.getGroupname());
        if (!commonValidations(group, cmd.getGroupname(), cmd.getSource(), cmd.getTarget()))
            return;

        // Schedule unmuting of user after a period of time
        Cancellable cancel = scheduler.scheduleOnce(
                FiniteDuration.apply(cmd.getDuration(), TimeUnit.MILLISECONDS),
                () -> {
                    group.unmuteMember(cmd.getTarget()); // Unmute user in group
                    // Inform user about unmuting
                    onlineUsers.get(cmd.getTarget()).getCommunicationActor().tell(
                            new TextData(cmd.getSource(), cmd.getGroupname(),
                                    "You have been unmuted! Muting time is up!"), self());
                },
                context().system().dispatcher());

        // Mute user in group
        group.muteMember(cmd.getTarget(), cmd.getDuration(), cancel);
        // Notify sender about success
        sender().tell(new Success(onlineUsers.get(cmd.getTarget())), self());
    }

    private void handleCoAdminGroupCommand(CoAdminGroupCommand cmd) {
        // Validations
        Group group = groups.get(cmd.getGroupname());
        if (!commonValidations(group, cmd.getGroupname(), cmd.getSource(), cmd.getTarget()))
            return;

        User user = onlineUsers.get(cmd.getTarget());
        // Notify sender about success
        sender().tell(new Success(user), self());

        // Determine desired new state
        State state = State.COADMIN;
        if (cmd instanceof CoAdminRemove)
            state = State.USER;

        // Set new member state
        group.setMemberState(user.getUsername(), state);
    }

    private void handleGroupRemove(GroupRemove cmd) {
        // Validations
        Group group = groups.get(cmd.getGroupname());

        if (!commonValidations(group, cmd.getGroupname(), cmd.getSource(), cmd.getTarget()))
            return;

        User user = onlineUsers.get(cmd.getTarget());
        // Notify sender about success
        sender().tell(new Success(user), self());
        group.removeMember(user); //Remove user from group
    }

    private void handleGroupAdd(GroupAdd cmd) {
        // Validations
        if (!onlineUsers.containsKey(cmd.getTarget()) || !groups.containsKey(cmd.getGroupname())) {
            System.out.println("adding to group failed");
            return;
        }

        User user = onlineUsers.get(cmd.getTarget());
        Group group = groups.get(cmd.getGroupname());
        // Add user to group members
        group.addMember(user, State.USER);
    }

    private void handleInviter(Inviter cmd) {
        // Validations
        Group group = groups.get(cmd.getGroupname());

        if (!(validateGroupExists(cmd.getGroupname()) &&
                validateTargetExists(cmd.getTarget()) &&
                validateSourcePrivileges(group, cmd.getSource()) &&
                validateNonMembership(group, cmd.getTarget())))
            return;

        // Notify sender about success
        sender().tell(new Success(onlineUsers.get(cmd.getTarget())), self());
    }

    private void handleGroupData(Data data) {
        // Validations
        if (!validateGroupExists(data.getTarget()))
            return;

        Group group = groups.get(data.getTarget());
        if (!validateSourceMembership(group, data.getSource())) {
            return;
        }

        // Verify source is not muted
        if (group.isMemberState(data.getSource(), State.MUTE)) {
            sender().tell(new Failure(Reason.MUTED, group.getMuteDuration(data.getSource()).toString()), self());
            return;
        }

        // Notify sender about success
        sender().tell(new Success(), self());
        // Broadcast message to group members
        group.getRouter().route(data, sender());
    }

    private void handleLeaveGroup(LeaveGroup cmd) {
        // Validations
        if (!validateGroupExists(cmd.getGroupname()))
            return;

        Group group = groups.get(cmd.getGroupname());
        if (!validateSourceMembership(group, cmd.getUsername())) {
            return;
        }

        // Notify sender about success
        sender().tell(new Success(), self());
        // Leave group
        leaveGroup(group, cmd.getUsername());
    }

    // Perform leave group operation for user
    private void leaveGroup(Group group, String username) {
        if (group.isMemberState(username, State.ADMIN)) { // Admin user
            // Broadcast group closing message
            group.getRouter().route(new TextData(username, group.groupname, group.getGroupname() + " admin has closed " + group.getGroupname() + "!"), self());
            // Remove group
            groups.remove(group.getGroupname());
        } else { // Other user
            User user = onlineUsers.get(username);
            group.removeMember(user); // Remove user from group
            // Broadcast message about member leaving group
            group.getRouter().route(new TextData(username, group.groupname, username + " has left " + group.groupname + "!"), self());
        }
    }

    private void handleCreateGroup(CreateGroup cmd) {
        // Validations
        if (groups.containsKey(cmd.getGroupname())) {
            sender().tell(new Failure(), self());
            return;

        } else {
            // Build routee list containing only creator
            List<Routee> routees = new ArrayList<>();
            User user = onlineUsers.get(cmd.getCreator());
            if (user == null) {
                System.out.println("creator is not online");
                return;
            }
            routees.add(new ActorRefRoutee(user.getCommunicationActor()));
            // Create router with broadcasting logic
            Router router = new Router(new BroadcastRoutingLogic(), routees);
            // Create new group
            Group group = new Group(router, cmd.getGroupname(), cmd.getCreator());
            groups.put(cmd.getGroupname(), group);
            // Notify sender about success
            sender().tell(new Success(), self());
        }
    }

    private void handleUserCommand(UserCommand cmd) {
        // Fetch user information
        User user = onlineUsers.getOrDefault(cmd.getTarget(), new User(null, null));
        if (user != null) // Success, nofity sender
            sender().tell(new Success(user), self());
        else { // Failure, user does not exist
            sender().tell(new Failure(), self());
            return;
        }
    }

    private void handleConnect(Connect cmd) {
        User user = cmd.getUser();
        // Verify username is not used
        if (onlineUsers.containsKey(user.getUsername()))
            sender().tell(new Failure(), self());
        else {
            // Add new online user
            onlineUsers.put(user.getUsername(), user);
            // Notify sender about success
            sender().tell(new Success(), self());
        }
    }

    private void handleDisconnect(Disconnect cmd) {
        ArrayList<Group> toLeave = new ArrayList<>(); // List of groups to leave
        for (Map.Entry<String, Group> entry : groups.entrySet()) {
            Group group = entry.getValue();
            // If user is a member of the group, add it to list
            if (group.isMember(cmd.getUsername())) {
                toLeave.add(group);
            }
        }

        // Leave all groups (close group if user is the admin)
        for (Group group : toLeave)
            leaveGroup(group, cmd.getUsername());

        // Remove user from online users
        onlineUsers.remove(cmd.getUsername());
        // Notify sender about success
        sender().tell(new Success(), self());
    }
}
