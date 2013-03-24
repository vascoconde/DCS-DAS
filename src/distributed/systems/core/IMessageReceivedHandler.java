package distributed.systems.core;


public interface IMessageReceivedHandler {
	public Message onMessageReceived(Message message);
	//public ControlMessage onExceptionThrown(Message message, InetSocketAddress destinationAddress);
}
