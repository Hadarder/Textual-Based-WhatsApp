import java.io.Serializable;

// Command class to be created and passed between actors
abstract class Command implements Serializable {
}

abstract class ConnectionCommand extends Command {
}

class Connect extends ConnectionCommand {
    private User user;

    public Connect(User user) {
        this.user = user;
    }

    public User getUser() {
        return user;
    }
}

class Disconnect extends ConnectionCommand {
    private String username;

    public Disconnect(String username) {
        this.username = username;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }
}

abstract class UserCommand extends Command {
    private String source;
    private String target;
    private String data;

    public String getSource() {
        return source;
    }

    public String getData() {
        return data;
    }

    public String getTarget() {
        return target;
    }

    public UserCommand(String source, String target, String data) {
        this.source = source;
        this.target = target;
        this.data = data;
    }

    public void setSource(String source) {
        this.source = source;
    }
}

class UserText extends UserCommand {
    public UserText(String source, String target, String message) {
        super(source, target, message);
    }
}

class UserFile extends UserCommand {
    public UserFile(String source, String target, String filename) {
        super(source, target, filename);
    }
}

abstract class GroupCommand extends Command {
    private String groupname;

    public GroupCommand(String groupname) {
        this.groupname = groupname;
    }

    public String getGroupname() {
        return groupname;
    }
}

class LeaveGroup extends GroupCommand {
    private String username;

    public LeaveGroup(String groupname, String username) {
        super(groupname);
        this.username = username;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }
}

class CreateGroup extends GroupCommand {
    private String creator;

    public CreateGroup(String groupname, String creator) {
        super(groupname);
        this.creator = creator;
    }

    public void setCreator(String creator) {
        this.creator = creator;
    }

    public String getCreator() {
        return creator;
    }
}

class GroupText extends GroupCommand {
    private String source;
    private String message;

    public GroupText(String groupname, String source, String message) {
        super(groupname);
        this.source = source;
        this.message = message;
    }

    public String getSource() {
        return source;
    }

    public String getMessage() {
        return message;
    }

    public void setSource(String source) {
        this.source = source;
    }
}

class GroupFile extends GroupCommand {
    private String source;
    private String filename;

    public GroupFile(String groupname, String source, String filename) {
        super(groupname);
        this.source = source;
        this.filename = filename;
    }

    public String getSource() {
        return source;
    }

    public String getFilename() {
        return filename;
    }

    public void setSource(String source) {
        this.source = source;
    }
}

class GroupInvite extends GroupCommand {
    private String source;
    private String target;

    public GroupInvite(String groupname, String source, String target) {
        super(groupname);
        this.source = source;
        this.target = target;
    }

    public String getSource() {
        return source;
    }

    public String getTarget() {
        return target;
    }

    public void setSource(String source) {
        this.source = source;
    }
}

class Inviter extends GroupInvite {
    public Inviter(String groupname, String source, String target) {
        super(groupname, source, target);
    }
}

class Invited extends GroupInvite {
    public Invited(String groupname, String source, String target) {
        super(groupname, source, target);
    }
}

class GroupAdd extends GroupInvite {
    public GroupAdd(String groupname, String source, String target) {
        super(groupname, source, target);
    }
}

class GroupRemove extends GroupCommand {
    private String source;
    private String target;

    public GroupRemove(String groupname, String source, String target) {
        super(groupname);
        this.source = source;
        this.target = target;
    }

    public String getTarget() {
        return target;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }
}

class CoAdminGroupCommand extends GroupCommand {
    private String source;
    private String target;

    public CoAdminGroupCommand(String groupname, String source, String target) {
        super(groupname);
        this.source = source;
        this.target = target;
    }

    public String getTarget() {
        return target;
    }

    public String getSource() {
        return source;
    }

    public void setSource(String source) {
        this.source = source;
    }
}

class CoAdminAdd extends CoAdminGroupCommand {
    public CoAdminAdd(String groupname, String source, String target) {
        super(groupname, source, target);
    }
}

class CoAdminRemove extends CoAdminGroupCommand {
    public CoAdminRemove(String groupname, String source, String target) {
        super(groupname, source, target);
    }
}

class MuteCommand extends GroupCommand {
    private String source;
    private String target;

    public MuteCommand(String groupname, String source, String target) {
        super(groupname);
        this.source = source;
        this.target = target;
    }

    public String getSource() {
        return source;
    }

    public String getTarget() {
        return target;
    }

    public void setSource(String source) {
        this.source = source;
    }
}

class MuteMember extends MuteCommand {
    private Long duration;

    public MuteMember(String groupname, String source, String target, Long duration) {
        super(groupname, source, target);
        this.duration = duration;
    }

    public Long getDuration() {
        return duration;
    }
}

class UnmuteMember extends MuteCommand {
    public UnmuteMember(String groupname, String source, String target) {
        super(groupname, source, target);
    }
}