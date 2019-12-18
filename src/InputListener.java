
// An interface to be implemented by everyone interested in input (arriving packets) events
interface InputListener {
    boolean InputArrived(Packet packet); //will return true if the event was handled well (meaning the packet forwarded)

}
