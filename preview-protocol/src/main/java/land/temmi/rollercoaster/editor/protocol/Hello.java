package land.temmi.rollercoaster.editor.protocol;

/** First message the preview process sends after connecting to the editor. */
public final class Hello {
    public int protocolVersion;

    public Hello() {
    }

    public Hello(int protocolVersion) {
        this.protocolVersion = protocolVersion;
    }
}
