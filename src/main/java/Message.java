import java.io.Serializable;

public abstract class Message implements Serializable {
    private String message;

    public Message(String message) {
        this.message = message;
    }

    public String getMessage() {
        return message;
    }
}

class PrintMessage extends Message {
    public PrintMessage(String message) {
        super(message);
    }
}

class ProcessMessage extends Message {
    public ProcessMessage(String message) {
        super(message);
    }
}
