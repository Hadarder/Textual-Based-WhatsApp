import java.io.Serializable;

enum Reason {GROUPNAME, SOURCE, MUTED, TARGET, INGROUP, NOTINGROUP, FORBIDDEN, NOTMUTED}

public abstract class Response implements Serializable {}

class Success extends Response {
    private User user;

    public Success(User user) {
        this.user = user;
    }

    public Success() {}

    public User getUser() {
        return user;
    }
}

class Failure extends Response {
    private Reason reason;
    private String data;

    public Failure(Reason reason, String data) {
        this.reason = reason;
        this.data = data;
    }

    public Reason getReason() {
        return reason;
    }

    public String getData() {
        return data;
    }

    public Failure() {}
}

class InviteResponse extends Response {}
class Confirm extends InviteResponse {}
class Decline extends InviteResponse {}