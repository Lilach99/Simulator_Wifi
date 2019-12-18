public class DataPacket extends Packet {

    public DataPacket(Device src, Device connector, Device dst, Standard standard, int lenght, PType type, String payload, boolean need_ack) {
        super(src, connector, dst, standard, lenght, type, need_ack, payload);
    }
}
