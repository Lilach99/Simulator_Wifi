import javafx.util.Pair;

import java.util.Timer;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class InputHandler implements InputListener {

    Device dev;

    public InputHandler(Device dev) {
        this.dev = dev;
    }

    @Override
    public synchronized boolean InputArrived(Packet packet) {

        System.out.println(this.toString()+"started input arrived");

        //only now the packet really arrived and not collided or got lost for some other reason
        //so, we can take care of it, the cleanup service will delete its busy interval from the relevant buffer of the medium
        if (packet.type == PType.CONTROL) {
            if(!dev.ctrl_buffer.contains((ControlPacket) packet))
                dev.ctrl_buffer.add((ControlPacket) packet);
            //System.out.println("ACK received by device " + this.toString());
        } else {
            if(!dev.buffer.contains(packet))
                dev.buffer.add(packet);
            //System.out.println("A Packet "+packet.toString()+" Arrived to device" + this.toString());
        }
        if (packet.type == PType.MANAGMENT) //assume its probe, that's what we have now
        {
            switch (packet.payload) {
                case "ProbeReq":
                    dev.probeResponse(packet);
                    break;
                case "ProbeRes":
                    dev.probe = true;
                    break;
                case "AuthReq":
                    dev.authResponse(packet);
                    break;
                case "AuthRes":
                    dev.auth = true;
                    break;
                case "AsscReq":
                    dev.asscResponse(packet);
                    break;
                case "AsscRes":
                    dev.assc = true;
                    break;
                default:
                    break; //do nothing
            }
        }
        if (packet.type == PType.DATA) {
            //maybe we have to ack it!
            if (packet.need_ack)
            {
                Packet ackPack = prepareACK(packet);
                dev.sending_buffer.add(ackPack);
                //dev.sendACK(packet);
            }
        }
        return true;
    }

    public ControlPacket prepareACK(Packet packet) {
        Device dst = packet.getSrc(); //we are about to answer the probe request sender
        Medium medForAck = dev.net.getWorld().get(new Pair<>(dst, dev));
        if (medForAck == null) { //the order was wrong
            medForAck = dev.net.getWorld().get(new Pair<>(dev, dst));
        }
        ControlPacket ack = new ControlPacket(dev, null, dst, medForAck.getStandard(), 5, PType.CONTROL, SType.ACK, "ACK!", false, packet);
        return ack;
    }

}
