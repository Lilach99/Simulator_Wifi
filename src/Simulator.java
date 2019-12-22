import java.sql.Timestamp;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Random;

public class Simulator {
    //a "knows all" class, choose the packets sending times in advance (randomly)
    //so it can know in advance which packets (if any) would be collided
    Network net;
    Device dev;
    LinkedList<Double> rates;
    int numOfPackets; //the number each device should send during the simulation
    LinkedList<Timestamp> devSendingTimes;
    LinkedList<Timestamp> APSendingTimes;
    //the beginning and the end of the interval from which the spaces between the packets are drawn
    int begInt; //in nanoseconds (10^-9 second)
    int endInt; //in nanoseconds (10^-9 second)

    public Simulator(int numOfPackets, String ssid, String APmacAddr, int APTimeout, LinkedList<Double> rates, String devName, String devMacAddr, int devTimeout) {
        this.numOfPackets = numOfPackets;
        this.rates = rates;
        this.net = new Network(ssid, APmacAddr, new Standard(Name.N), rates, APTimeout);
        this.dev = net.createDevice(devName, devMacAddr, rates, new Standard(Name.N), devTimeout, net.AP);
        net.AP.setDestination(dev); //the net AP will be sending packets to dev
        this.devSendingTimes = new LinkedList<>();
        this.APSendingTimes = new LinkedList<>();
    }

    public Network getNet() {
        return net;
    }

    public void setNet(Network net) {
        this.net = net;
    }

    public Device getDev() {
        return dev;
    }

    public void setDev(Device dev) {
        this.dev = dev;
    }

    public LinkedList<Double> getRates() {
        return rates;
    }

    public void setRates(LinkedList<Double> rates) {
        this.rates = rates;
    }

    public void drawPacketsTimes() {
        //draw (uniformly) random the spaces between the sending actions
        //the spaces are drawn from the interval [begInt, EndInt]
        //the corresponding timestamps are calculated and stored in the linked lists
        Random r = new Random();
        Date date = new Date();
        Timestamp initTimestamp = new Timestamp(date.getTime());
        initTimestamp.setNanos((int) Math.pow(10, 9)); //add one second - for security...
        Timestamp toSend = initTimestamp;
        int space;
        //generating the sending times for the device
        for (int i = 0; i < numOfPackets; i++) {
            space = r.nextInt(endInt - begInt) + begInt; //in nanoseconds
            toSend.setNanos(toSend.getNanos()+space); //this is the next sending timestamp
            devSendingTimes.add(toSend); //there are not gonna be two identical sending time for the same device, it's OK
        }
        //generating the sending times for the AP
        for (int i = 0; i < numOfPackets; i++) {
            space = r.nextInt(endInt - begInt) + begInt; //in nanoseconds
            toSend.setNanos(toSend.getNanos()+space); //this is the next sending timestamp
            devSendingTimes.add(toSend); //there are not gonna be two identical sending time for the same device, it's OK
        }
        //Note: it could be that the AP and the device choose close enough timestamps, and then a collision might happen!
    }

    public Network simulate() { //testing function, for now running communication from a station (device) and an AP

        return net;
    }
}
