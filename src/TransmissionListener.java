import java.util.*;

// An interface to be implemented by everyone interested in sending packets events
interface TransmissionListener {
    boolean PacketSent(Packet packet, boolean loss); //will return true if the event was handled well (meaning the packet forwarded)
}

