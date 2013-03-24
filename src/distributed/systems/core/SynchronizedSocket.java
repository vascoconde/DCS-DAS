package distributed.systems.core;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;

public class SynchronizedSocket extends Thread {

	private ServerSocket serverSocket;
	private IMessageReceivedHandler handler;
	private String localUrl;
	private int localPort;

	public SynchronizedSocket(String localUrl, int localPort) {
		this.localUrl = localUrl;
		this.localPort = localPort;
		try {
			serverSocket = new ServerSocket(localPort);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	

	public class ConnectionHandler extends Thread {
		private Socket s;
		private IMessageReceivedHandler handler;
		public ConnectionHandler(Socket socket, IMessageReceivedHandler handler) {
			s = socket;
			this.handler =  handler;
			
		}
		
		public void run() {
			ObjectOutputStream out;
			ObjectInputStream in;
			try {
				in = new ObjectInputStream(s.getInputStream());

				Message msg = (Message)in.readObject();
				//handler.onMessageReceived(msg);
				Message replyMsg = handler.onMessageReceived(msg);

				if(replyMsg != null) {
					out = new ObjectOutputStream(s.getOutputStream());
					out.writeObject(replyMsg);
					out.flush();
					out.close();

				}
				
				in.close();
				s.close();

			} catch (IOException | ClassNotFoundException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}	
		}
	}

	@Override
	public void run() {
		while(true) {
			try {
				Socket s = serverSocket.accept();
				Thread t = new ConnectionHandler(s, handler);
				t.start();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}		
	}

	public void addMessageReceivedHandler(IMessageReceivedHandler handler) {
		this.handler = handler;
		Thread t = new Thread( this);
		t.start();
	}
	
	/*
	public void sendMessage(ControlMessage cMessage, InetSocketAddress address) {
		try {
			Socket socket = new Socket(address.getAddress(), address.getPort());
			ObjectOutputStream out = new ObjectOutputStream(socket.getOutputStream());
			out.writeObject(cMessage);
			out.close();
			
			socket.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}*/
	
}
