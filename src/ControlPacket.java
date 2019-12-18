
enum SType{
    ACK;
}
public class ControlPacket extends Packet {

    SType subtype;
    Packet packet_ack; //the packet this ControlPacket is "ackking"

    public SType getSubtype() {
        return subtype;
    }

    public ControlPacket(Device src, Device connector, Device dst, Standard standard, int lenght, PType type, SType subtype, String payload, boolean need_ack, Packet packet) {
        super(src, connector, dst, standard, lenght, type, need_ack, payload);
        this.subtype = subtype;
        this.packet_ack = packet;
    }
}
