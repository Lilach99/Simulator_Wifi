import java.util.LinkedList;

public class ManagementPacket extends Packet {

    //payload, only for probe now:
    String ssid;
    //LinkedList<Double> sup_rates;

    public ManagementPacket(Device src, Device connector, Device dst, Standard standard, int lenght, PType type, String ssid, LinkedList<Double> sup_rates, boolean need_ack) {
        super(src, connector, dst, standard, lenght, type, need_ack);
        this.ssid = ssid;
        //this.sup_rates = sup_rates;

    }
}
