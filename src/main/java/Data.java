import java.io.Serializable;

abstract class Data<T> implements Serializable {
    private String source;
    private String target;
    private T data;

    public Data(String source, String target, T data) {
        this.source = source;
        this.target = target;
        this.data = data;
    }

    public String getSource() {
        return source;
    }
    public T getData() {
        return data;
    }

    public String getTarget() {
        return target;
    }
}

class TextData extends Data<String> {
    private String data;

    public TextData(String source, String target, String data) {
        super(source, target, data);
    }
}

class FileData extends Data<byte[]> {
    private byte[] data;
    private String filename;

    public FileData(String source, String target, byte[] data, String filename) {
        super(source, target, data);
        this.filename = filename;
    }

    public String getFilename() {
        return filename;
    }
}
