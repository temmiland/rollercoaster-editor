package land.temmi.rollercoaster.editor.protocol;

/** The editor's reply to {@link Hello}. A rejected handshake carries a human-readable reason. */
public final class HelloAck {
    public boolean accepted;
    public String reason;

    public HelloAck() {
    }

    public HelloAck(boolean accepted, String reason) {
        this.accepted = accepted;
        this.reason = reason;
    }
}
