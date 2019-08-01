import akka.actor.Cancellable;
import akka.routing.Router;
import javafx.util.Pair;

import java.io.Serializable;
import java.util.HashMap;

enum State {ADMIN, COADMIN, USER, MUTE}

public class Group implements Serializable {
    private Router router; // Router of the group
    String groupname; // Group name
    private HashMap<String, State> members = new HashMap<>(); // Members of the group and their state
    // Muted members and their mute duration and cancel mute event
    private HashMap<String, Pair<Long, Cancellable>> mutedMembers = new HashMap<>();

    public Group(Router router, String groupname, String admin) {
        this.router = router;
        this.groupname = groupname;
        members.put(admin, State.ADMIN);
    }

    public Router getRouter() {
        return router;
    }

    public String getGroupname() {
        return groupname;
    }

    public HashMap<String, State> getMembers() {
        return members;
    }

    // Add member to map and to router routees
    public void addMember(User user, State state) {
        members.put(user.getUsername(), state);
        router = router.addRoutee(user.getCommunicationActor());
    }

    // Remove member from map and router routees
    public void removeMember(User user) {
        members.remove(user.getUsername());
        router = router.removeRoutee(user.getCommunicationActor());
    }

    public boolean isMember(String username) {
        return members.containsKey(username);
    }

    public boolean isMemberState(String username, State state) {
        if (isMember(username))
            return members.get(username) == state;
        return false;
    }

    public void setMemberState(String username, State state) {
        members.put(username, state);
    }

    // Change member state to MUTE and add it to muted list
    public void muteMember(String username, Long duration, Cancellable cancel) {
        if (isMember(username)) {
            setMemberState(username, State.MUTE);
            mutedMembers.put(username, new Pair<>(duration, cancel));
        }
    }

    // Change member state, remove it from list and cancel future unmute event
    public void unmuteMember(String username) {
        setMemberState(username, State.USER);
        Pair<Long, Cancellable> pair = mutedMembers.remove(username);
        if (pair != null)
            pair.getValue().cancel();
    }

    public Long getMuteDuration(String username) {
        if (mutedMembers.containsKey(username))
            return mutedMembers.get(username).getKey();
        return 0L;
    }
}
