import java.util.Timer;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class InputHandler implements Runnable {

    Device dev;
    ScheduledExecutorService exec = Executors.newSingleThreadScheduledExecutor();

    @Override
    public void run() {
        exec.scheduleAtFixedRate(() -> {
            try {
                Thread.sleep(1000);
                //delay of 1 second is needed for it to synchronize correctly with the buffer changes!
                //the forwardPacketsInRate method take care of this problem by multiplying the sending rate.
            } catch (InterruptedException e) {
                e.printStackTrace();
            }

            Packet packet = this.dev.removePacketFromBuff(); //removes the top packet from the device's buffer
                if(packet!=null) {
                    System.out.println("Packet Really Found!!!");
                    if(packet.isNeed_ack()) {
                        //now we have to send an ACK on this packet
                        ControlPacket ack = new ControlPacket(this.dev, this.dev.net.getAP(), packet.getSrc(), packet.getStandard(), 5, PType.CONTROL, SType.ACK, packet.getTs().toString(), false, packet);
                        try {
                            Thread.sleep(100);
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                        this.dev.sendPacket(ack, true);
                    }
                }

                //assume that losing a packet causes drop in the rate -
                // losing a packet "takes the same time" as sending one

        }, 0, 1, TimeUnit.SECONDS);
    }
}
