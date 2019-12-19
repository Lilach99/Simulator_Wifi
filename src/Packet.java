import java.io.Serializable;
import java.sql.Timestamp;
import java.util.Date;

enum PType {
    DATA,
    CONTROL,
    MANAGMENT;
}

public class Packet implements Comparable<Packet>, Serializable {

    Timestamp creation_ts; //the time when this packet was created
    Timestamp sending_ts; //the time when the packet was actually sent, initialized  to null
    Timestamp arrival_ts; //the time when the packet arrived to its destination
    transient Device src;
    transient Device connector; //the AP which will deliver the packet
    transient Device dst;
    Standard standard;
    int length; //in bits
    PType type;
    boolean need_ack;
    String payload;
    boolean lost; //identifies whether the packet got lost in her way to the destination (due to collision, noise, etc.)

    public Packet(Device src, Device connector, Device dst, Standard standard, int lenght, PType type, boolean need_ack) {
        Date date = new Date();
        this.creation_ts = new Timestamp(date.getTime());
        this.src = src; //ADDR1
        this.connector = connector; //ADDR2, will not be used if it is a p2p connection (there is no connector between the 2 communicating devices)
        this.dst = dst; //ADDR3
        this.standard = standard;
        this.length = lenght;
        this.type = type;
        this.need_ack = need_ack;
        this.sending_ts = null;
        this.lost = false;
    }

    public Packet(Device src, Device connector, Device dst, Standard standard, int lenght, PType type, boolean need_ack, String payload) {
        Date date = new Date();
        this.creation_ts = new Timestamp(date.getTime());
        this.src = src; //ADDR1
        this.connector = connector; //ADDR2, will not be used if it is a p2p connection (there is no connector between the 2 communicating devices)
        this.dst = dst; //ADDR3
        this.standard = standard;
        this.length = lenght;
        this.type = type;
        this.need_ack = need_ack;
        this.sending_ts = null;
        this.payload = payload;
        this.lost = false;
    }

    public Timestamp getSending_ts() {
        return sending_ts;
    }

    public void setSending_ts(Timestamp sending_ts) {
        this.sending_ts = sending_ts;
    }

    public Standard getStandard() {
        return standard;
    }

    public Device getDst() {
        return dst;
    }

    public Timestamp getTs() {
        return creation_ts;
    }

    public PType getType() {
        return type;
    }

    public void setType(PType type) {
        this.type = type;
    }

    public boolean isNeed_ack() {
        return need_ack;
    }

    public void setNeed_ack(boolean need_ack) {
        this.need_ack = need_ack;
    }

    public Device getSrc() {
        return src;
    }

    public Device getConnector() {
        return connector;
    }

    public void setConnector(Device connector) {
        this.connector = connector;
    }

    public int getLength() {
        return length;
    }

    public void setDst(Device dst) {
        this.dst = dst;
    }

    public void setLength(int length) {
        this.length = length;
    }

    public Timestamp getArrival_ts() {
        return arrival_ts;
    }

    public void setArrival_ts(Timestamp arrival_ts) {
        this.arrival_ts = arrival_ts;
    }

    @Override
    public int compareTo(Packet o) { //we need that function because we order the packets in a priority queue
        return this.getTs().compareTo(o.getTs()); //a packet priority is determined by its timestamp
    }

    public void Lost() { //losing the packet - marking it sa a lost one
        this.lost = true;
    }
}
