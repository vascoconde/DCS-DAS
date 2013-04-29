package distributed.systems.core;
import java.net.InetSocketAddress;

public interface IMessageReceivedHandler {
	public Message onMessageReceived(Message message);
	public Message onExceptionThrown(Message message, InetSocketAddress destinationAddress);
}
