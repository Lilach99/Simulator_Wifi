import javafx.util.Pair;

import java.io.Serializable;
import java.util.*;

enum Mode{
    AD_HOC,
    INFRASTRUCTURE;
}

public class Network implements Serializable {
    AP AP; //the network manager
    String ssid; //the network name
    HashMap<Pair<Device, Device>, Medium> world;    //only for Ad Hoc maybe...
    LinkedList<Device> devices;
    LinkedList<Medium> mediums;
    Mode mode;

    public Network(String ssid, String MAC, Standard s_standard, LinkedList<Double> rates, long timeout) {
        this.world = new HashMap<>();
        this.mediums = new LinkedList<>();
        this.devices = new LinkedList<>();
        this.ssid = ssid;
        this.AP = new AP(ssid, MAC, rates, s_standard, this, timeout);
        this.mode=Mode.INFRASTRUCTURE; //for now only infrastructure
    }

    public HashMap<Pair<Device, Device>, Medium> getWorld() {
        return world;
    }

    public void setWorld(HashMap<Pair<Device, Device>, Medium> world) {
        this.world = world;
    }

    public LinkedList<Medium> getMediums() {
        return mediums;
    }

    public void setMediums(LinkedList<Medium> mediums) {
        this.mediums = mediums;
    }

    public LinkedList<Device> getDevices() {
        return devices;
    }

    public Device getAP() {
        return AP;
    }

    public void setAP(AP AP) {
        this.AP = AP;
    }

    public String getSsid() {
        return ssid;
    }

    public void setSsid(String ssid) {
        this.ssid = ssid;
    }

    public Device createDevice(String name, String MAC_addr, LinkedList<Double> rates, Standard sup_standard, long timeout, Device destination) {
        return new Device(name, MAC_addr, rates, sup_standard, this, timeout, destination);
    }

    public boolean addDevice(Device dev, double plp){
        //means the device is in the network AP effective range.
        //automatically creates a NANA channel connecting the new device and the AP
        if(devices.indexOf(dev)==-1){  //the device is really new

            devices.add(dev);

            //adds a medium between the device and the network Access Point
            Medium med = this.createMedium(AP, dev, plp, 500, 11); // TODO: decide how to choose the wifi channel number (1-14).
            mediums.add(med);
            this.AP.connected_devs.put(dev, med);
            dev.addConnectedDev(this.AP, med);
            this.world.put(new Pair<>(dev, this.AP), med); //Device and than AP!!!

            dev.addListener(AP, med);
            AP.addListener(dev, med);

            dev.connect(); //have to go through probe, auth, assoc
            System.out.println("Ready to communicate!");
            //updating the channels' communication states
            med.setComState(ComState.AA);

            return true;
        }
        return false; //the device already exists
    }
/*
    //we do not need this for Infrastructure network, only only for Ad Hoc
    public Channel addConnection(Device dev1, Device dev2, double plp, double dist, int cnum){
        //assuming both devices already exist, the channel will not be directed
        Pair<Device, Device> p1 = new Pair<>(dev1, dev2);
        if(!(world.containsKey(p1))) {  //dev1->dev2 channel does not already exists
            Channel ch = createChannel(dev1, dev2, plp, dist, cnum);
            if (ch.status == true) { //channel creation succeeded
                this.channels.add(ch);
                this.world.put(p1, ch); //does not matter if we take p1 or p2
                return ch;
            }
        }
        return null;
    }

 */

    public Medium createMedium(Device dev1, Device dev2, double plp, double dist, int cnum){
        //tries to create a medium between 2 devices
        //returns a medium with status 1 iff the creation succeeded (all of the parameters are OK)
        final LinkedList<Double> lcopy = new LinkedList<>(dev1.getRates());
        lcopy.retainAll(dev2.getRates());
        Collections.sort(lcopy); //if there are more than 1 common supported rates, we pick the largest one
        if(dev1.getSup_standard().getName() == dev2.getSup_standard().getName() && !lcopy.isEmpty()){
            Medium med = new Medium(dev1, dev2, cnum, (Double)lcopy.getLast(), dist, plp, dev1.getSup_standard(), this);
            return med;
        }
        else{
            return new Medium(false); //the creation did not succeed due to a problem in the parameters, so we return a medium with status 0
        }
    }

    public void setDevices(LinkedList<Device> devices) {
        this.devices = devices;
    }

}
