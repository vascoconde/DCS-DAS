package distributed.systems.das;

import java.net.InetSocketAddress;
import java.util.HashSet;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

import distributed.systems.core.IMessageReceivedHandler;
import distributed.systems.core.Message;
import distributed.systems.core.SynchronizedClientSocket;
import distributed.systems.core.SynchronizedSocket;
import distributed.systems.das.units.Player;
import distributed.systems.das.units.Unit;

/**
 * The actual battlefield where the fighting takes place.
 * It consists of an array of a certain width and height.
 * 
 * It is a singleton, which can be requested by the 
 * getBattleField() method. A unit can be put onto the
 * battlefield by using the putUnit() method.
 * 
 * @author Pieter Anemaet, Boaz Pat-El
 */
public class BattleField implements IMessageReceivedHandler {
	/* The array of units */
	private Unit[][] map;
	private ConcurrentHashMap<InetSocketAddress, Unit> units;


	/* The static singleton */
	//private static BattleField battlefield;

	/* Primary socket of the battlefield */ 
	//private Socket serverSocket;
	private SynchronizedSocket serverSocket;
	private String url;
	private int port;
	private final int timeout = 3000;


	private Map<ActionID, ActionInfo> pendingOutsideActions;
	private Map<Integer, ActionInfo> pendingOwnActions;
	private int localMessageCounter = 0;


	/* The last id that was assigned to an unit. This variable is used to
	 * enforce that each unit has its own unique id.
	 */
	private int lastUnitID = 0;

	public final static String serverID = "server";
	public final static int MAP_WIDTH = 25;
	public final static int MAP_HEIGHT = 25;
	//private ArrayList <Unit> units; 
	//private Map<InetSocketAddress, Integer> units; 

	private HashSet<InetSocketAddress> battlefields; 

	/**
	 * Initialize the battlefield to the specified size 
	 * @param width of the battlefield
	 * @param height of the battlefield
	 */
	BattleField(String url, int port) {
		battlefields = new HashSet<InetSocketAddress>();
		this.url = url;
		this.port = port;	
		battlefields.add(new InetSocketAddress(url, port));

		initBattleField();		
	}

	BattleField(String url, int port, String otherUrl, int otherPort) {
		battlefields = new HashSet<InetSocketAddress>();
		this.url = url;
		this.port = port;
		battlefields.add(new InetSocketAddress(url, port));
		initBattleField();

		Message message = new Message();
		message.put("request", MessageRequest.requestBFList);
		message.put("bfAddress", new InetSocketAddress(url, port));
		SynchronizedClientSocket clientSocket;
		clientSocket = new SynchronizedClientSocket(message, new InetSocketAddress(otherUrl, otherPort), this);
		clientSocket.sendMessageWithResponse();
	}

	private synchronized void initBattleField(){
		map = new Unit[MAP_WIDTH][MAP_WIDTH];
		units = new ConcurrentHashMap<InetSocketAddress, Unit>();

		serverSocket = new SynchronizedSocket(url, port);
		serverSocket.addMessageReceivedHandler(this);
		//units = new ArrayList<Unit>();
		pendingOwnActions = new ConcurrentHashMap<Integer, ActionInfo>();
		pendingOutsideActions = new ConcurrentHashMap<ActionID, ActionInfo>();
		//Updates to game state
		new Thread(new Runnable() {
			public void run() {
				SynchronizedClientSocket clientSocket;
				while(true) {

					for( InetSocketAddress address : units.keySet()) {
						Message message = new Message();
						message.put("request", MessageRequest.gameState);
						message.put("gamestate", map);
						clientSocket = new SynchronizedClientSocket(message, address, null);
						clientSocket.sendMessage();	
					}

					try {
						Thread.sleep(100L);//Time between gameState update is sent to units
					} catch (InterruptedException e) {
						e.printStackTrace();
					}		
				}
			}
		}).start();

	}

	/**
	 * Singleton method which returns the sole 
	 * instance of the battlefield.
	 * 
	 * @return the battlefield.
	 */
	/*
	public static BattleField getBattleField() {
		if (battlefield == null)
			battlefield = new BattleField(MAP_WIDTH, MAP_HEIGHT);
		return battlefield;
	}*/

	/**
	 * Puts a new unit at the specified position. First, it
	 * checks whether the position is empty, if not, it
	 * does nothing.
	 * In addition, the unit is also put in the list of known units.
	 * 
	 * @param unit is the actual unit being spawned 
	 * on the specified position.
	 * @param x is the x position.
	 * @param y is the y position.
	 * @return true when the unit has been put on the 
	 * specified position.
	 */
	private boolean spawnUnit(Unit unit, int x, int y)
	{
		synchronized (this) {
			if (map[x][y] != null)
				return false;

			map[x][y] = unit;
			unit.setPosition(x, y);
		}
		//units.add(unit);

		return true;
	}

	/**
	 * Put a unit at the specified position. First, it
	 * checks whether the position is empty, if not, it
	 * does nothing.
	 * 
	 * @param unit is the actual unit being put 
	 * on the specified position.
	 * @param x is the x position.
	 * @param y is the y position.
	 * @return true when the unit has been put on the 
	 * specified position.
	 */
	private synchronized boolean putUnit(Unit unit, int x, int y)
	{
		if (map[x][y] != null)
			return false;

		map[x][y] = unit;
		unit.setPosition(x, y);

		return true;
	}

	/**
	 * Get a unit from a position.
	 * 
	 * @param x position.
	 * @param y position.
	 * @return the unit at the specified position, or return
	 * null if there is no unit at that specific position.
	 */
	public Unit getUnit(int x, int y)
	{
		assert x >= 0 && x < map.length;
		assert y >= 0 && x < map[0].length;

		return map[x][y];
	}

	/**
	 * Move the specified unit a certain number of steps.
	 * 
	 * @param unit is the unit being moved.
	 * @param deltax is the delta in the x position.
	 * @param deltay is the delta in the y position.
	 * 
	 * @return true on success.
	 */
	private synchronized boolean moveUnit(Unit tUnit, int newX, int newY)
	{
		int originalX = tUnit.getX();
		int originalY = tUnit.getY();
		Unit unit = map[originalX][originalY];
		if(unit == null || !unit.equals(tUnit)) return false;
		//if(Math.abs(originalX - newX) > 1 || Math.abs(originalY - newY) > 1) return false;
		//System.out.println(originalX + " " + originalY + ":");
		if (unit.getHitPoints() <= 0)
			return false;

		if (newX >= 0 && newX < BattleField.MAP_WIDTH)
			if (newY >= 0 && newY < BattleField.MAP_HEIGHT)
				if (map[newX][newY] == null) {
					if (putUnit(unit, newX, newY)) {
						map[originalX][originalY] = null;
						return true;
					}
				}

		return false;
	}

	/**
	 * Remove a unit from a specific position and makes the unit disconnect from the server.
	 * 
	 * @param x position.
	 * @param y position.
	 */
	private synchronized void removeUnit(int x, int y)
	{
		Unit unitToRemove = this.getUnit(x, y);
		if (unitToRemove == null)
			return; // There was no unit here to remove
		map[x][y] = null;
		unitToRemove.disconnect();
		units.remove(unitToRemove);
	}

	/**
	 * Returns a new unique unit ID.
	 * @return int: a new unique unit ID.
	 */
	public synchronized int getNewUnitID() {
		return ++lastUnitID;
	}

	public Message onMessageReceived(Message msg) {

		//System.out.println("MESSAGE RECEIVED:" + msg.get("request"));

		System.out.println("MESSAGE RECEIVED " + (MessageRequest)msg.get("request"));

		if((Boolean)msg.get("sync") != null && (Boolean)msg.get("sync") == true) {
			System.out.println("SYNC MESSAGE RECEIVED " + (MessageRequest)msg.get("request"));
			processSyncMessage(msg);
			
		} else {
			MessageRequest request = (MessageRequest)msg.get("request");
			Message reply = null;
			String origin = (String)msg.get("origin");
			Unit unit;
			switch(request)
			{

			case spawnUnit:
			case moveUnit:
			case dealDamage:
			case healDamage:
				syncBF(msg);
				System.out.println("Mandou sync pa toda agente");
				break;
			case requestBFList: {
				reply = new Message();
				reply.put("request", MessageRequest.replyBFList);
				battlefields.add((InetSocketAddress)msg.get("bfAddress"));
				reply.put("bfList", battlefields);
				return reply;
			}

			case replyBFList: {
				HashSet<InetSocketAddress> bfList = (HashSet<InetSocketAddress>)msg.get("bfList");
				for(InetSocketAddress address: bfList) {
					battlefields.add(address);
				}
				for(InetSocketAddress address: battlefields) {
					SynchronizedClientSocket clientSocket;
					Message message = new Message();
					message.put("request", MessageRequest.addBF);
					message.put("bfAddress", new InetSocketAddress(url, port));
					clientSocket = new SynchronizedClientSocket(message,address, this);
					clientSocket.sendMessage();
				}
				//System.out.println("BATTLEFIELDS:"+ bfList.toString());

				//reply = new Message();
				//HashSet bfList = (HashSet<InetSocketAddress>)msg.get("bfList");
				//int y = (Integer)msg.get("y");
				//reply.put("id", msg.get("id"));
				return null;
			}

			case addBF: {
				battlefields.add((InetSocketAddress)msg.get("bfAddress"));
				//System.out.println("ADD BF:"+ battlefields.toString());

				return null;
			}

			//break;
			//case removeUnit:
			//this.removeUnit((Integer)msg.get("x"), (Integer)msg.get("y"));			
			//if(syncBF(msg)) return null;

			case SyncActionResponse: 
				processResponseMessage(msg);

				break;
			case SyncActionConfirm:
				return processConfirmMessage(msg);
			}
		}
		return null;

		//serverSocket.sendMessage(reply, origin);
		/*
		try {
			if (reply != null)
				serverSocket.sendMessage(reply, origin);
		}
		/*catch(IDNotAssignedException idnae)  {
			// Could happen if the target already logged out
		}*/
	}

	private synchronized Message processConfirmMessage(Message msg) {
		Integer messageID = (Integer)msg.get("serverMessageID");
		ActionInfo removeAction = pendingOutsideActions.remove(new ActionID(messageID, (InetSocketAddress)msg.get("address")));			
		if((Boolean)msg.get("confirm") && removeAction != null) {
			Unit unit = null;
			switch ((MessageRequest)removeAction.message.get("request")) {

			case spawnUnit: {
				System.out.println("BATTLE FIELD:Spawn" + port);
				System.out.println(battlefields.toString());

				Boolean succeded = this.spawnUnit((Unit)msg.get("unit"), (Integer)msg.get("x"), (Integer)msg.get("y"));
				if(succeded) {
					units.put((InetSocketAddress)msg.get("address"), (Unit)msg.get("unit"));	
				}
				Message reply = new Message();
				reply.put("request", MessageRequest.spawnAck);
				reply.put("succeded", succeded);
				reply.put("gamestate", map);
				return reply;

			}
			case dealDamage: {

				int x = (Integer)msg.get("x");
				int y = (Integer)msg.get("y");
				unit = this.getUnit(x, y);
				if (unit != null)
					unit.adjustHitPoints( -(Integer)msg.get("damage") );
				/* Copy the id of the message so that the unit knows 
				 * what message the battlefield responded to. 
				 */
				break;
			}
			case healDamage:
			{
				int x = (Integer)msg.get("x");
				int y = (Integer)msg.get("y");
				unit = this.getUnit(x, y);
				if (unit != null)
					unit.adjustHitPoints( (Integer)msg.get("healed") );
				/* Copy the id of the message so that the unit knows 
				 * what message the battlefield responded to. 
				 */

				break;
			}
			case moveUnit:
			{

				System.out.println("BATTLEFIELD: MOVEUNIT");
				Unit tempUnit = (Unit)msg.get("unit");
				/*
				if(temptUnit == null) {
					System.out.println("NULL");
				}*/

				boolean move = this.moveUnit((Unit)msg.get("unit"), (Integer)msg.get("x"), (Integer)msg.get("y"));
				if(!move) System.out.println("MOVE CANCELED");

				/* Copy the id of the message so that the unit knows 
				 * what message the battlefield responded to. 
				 */
				break;
			}
			default:
				break;
			}
		}
		return null;

	}

	private synchronized void processResponseMessage(Message msg) {
		Integer messageID = (Integer)msg.get("serverMessageID");
		ActionInfo actionInfo =  pendingOwnActions.get(messageID);

		Message message = new Message();
		message.put("request", MessageRequest.SyncActionConfirm);
		message.put("address", new InetSocketAddress(url, port));
		message.put("id", messageID);

		if(actionInfo != null) {
			if((Boolean)msg.get("ack")) {
				actionInfo.ackReceived.add((InetSocketAddress)msg.get("address")); 
				if(actionInfo.ackReceived.size() == battlefields.size()) {
					message.put("confirm", true);
					for(InetSocketAddress address : actionInfo.ackReceived) {
						SynchronizedClientSocket clientSocket = new SynchronizedClientSocket(message, address, this);
						clientSocket.sendMessage();

					}
					pendingOwnActions.remove(messageID).timer.cancel();
				}
			} else {
				pendingOwnActions.remove(messageID).timer.cancel();
				message.put("confirm", false);
				SynchronizedClientSocket clientSocket = new SynchronizedClientSocket(message, (InetSocketAddress)msg.get("address"), this);
				clientSocket.sendMessage();

			}

		} else {
			message.put("confirm", false);
			SynchronizedClientSocket clientSocket = new SynchronizedClientSocket(message, (InetSocketAddress)msg.get("address"), this);
			clientSocket.sendMessage();
		}

	}

	private synchronized void processSyncMessage(Message msg) {
		MessageRequest request = (MessageRequest)msg.get("request");
		Message reply = null;
		Unit unit = units.get((InetSocketAddress)msg.get("address"));

		Integer messageID = (Integer)msg.get("serverMessageID");
		InetSocketAddress originAddress = (InetSocketAddress)msg.get("address");
		Integer x = (Integer)msg.get("x");
		Integer y = (Integer)msg.get("y");
		boolean conflictFound = false; 
		Set<InetSocketAddress> toRemoveTemp = new HashSet<InetSocketAddress>();
		switch(request) {
		case spawnUnit: 
			if (getUnit(x, y) == null){
				for(ActionInfo info : pendingOwnActions.values()){

					MessageRequest actionType = (MessageRequest)info.message.get("request");
					if(actionType == MessageRequest.moveUnit || actionType == MessageRequest.spawnUnit){
						if(x.equals((Integer)info.message.get("x")) && y.equals((Integer)info.message.get("y"))) {
							//sendActionAck(msg, false, messageID, originAddress);
							conflictFound = true;
							break;
						} 
					}
				}
				for(ActionInfo info : pendingOutsideActions.values()){

					MessageRequest actionType = (MessageRequest)info.message.get("request");
					if(actionType == MessageRequest.moveUnit || actionType == MessageRequest.spawnUnit){
						if(x.equals((Integer)info.message.get("x")) && y.equals((Integer)info.message.get("y"))) {
							//sendActionAck(msg, false, messageID, originAddress);
							conflictFound = true;
							break;
						} 
					}
				}

			}
			else {
				conflictFound = true;
			}

			if(conflictFound) {
				sendActionAck(msg, false, messageID, originAddress);
			} else {
				addPendingOutsideAction(msg, messageID, originAddress);
				sendActionAck(msg, true, messageID, originAddress);
			}



			break;

		case moveUnit:
			if (getUnit(x, y) == null){
				for(ActionInfo info : pendingOwnActions.values()){
					MessageRequest actionType = (MessageRequest)info.message.get("request");
					if(actionType == MessageRequest.moveUnit || actionType == MessageRequest.spawnUnit){
						if(x.equals((Integer)info.message.get("x")) && y.equals((Integer)info.message.get("y"))) {
							conflictFound = true;
							break;
						} 
					} else if(actionType == MessageRequest.healDamage || actionType == MessageRequest.dealDamage) {
						if(unit.getX().equals((Integer)info.message.get("x")) && unit.getY().equals((Integer)info.message.get("y"))) {
							toRemoveTemp.add((InetSocketAddress)info.message.get("serverMessageID"));
						}
					}
				}
				for(ActionInfo info : pendingOutsideActions.values()){
					MessageRequest actionType = (MessageRequest)info.message.get("request");
					if(actionType == MessageRequest.moveUnit || actionType == MessageRequest.spawnUnit){
						if(x.equals((Integer)info.message.get("x")) && y.equals((Integer)info.message.get("y"))) {
							conflictFound = true;
							break;
						} 
					} 
				}

			}
			else {
				conflictFound = true;
			}

			if(conflictFound) {
				sendActionAck(msg, false, messageID, originAddress);
			} else {
				for(InetSocketAddress addressToRemove : toRemoveTemp) {
					pendingOwnActions.remove(addressToRemove).timer.cancel();
				}
				addPendingOutsideAction(msg, messageID, originAddress);
				sendActionAck(msg, true, messageID, originAddress);
			}

			break;

		case dealDamage:
		case healDamage: 
			if (getUnit(x, y) != null && getUnit(x, y) instanceof Player) {
				for(ActionInfo info : pendingOwnActions.values()){
					MessageRequest actionType = (MessageRequest)info.message.get("request");
					if(actionType == MessageRequest.moveUnit){
						Unit infoUnit = units.get((InetSocketAddress)info.message.get("address"));
						if(x.equals(infoUnit.getX()) && y.equals(infoUnit.getY())) {
							conflictFound = true;
							break;
						} 
					}
				} for(ActionInfo info : pendingOutsideActions.values()){
					MessageRequest actionType = (MessageRequest)info.message.get("request");
					if(actionType == MessageRequest.moveUnit){
						Unit infoUnit = units.get((InetSocketAddress)info.message.get("address"));
						if(x.equals(infoUnit.getX()) && y.equals(infoUnit.getY())) {
							conflictFound = true;
							break;
						} 
					}
				}
			}
			else {
				conflictFound = true;
			}

			if(conflictFound) {
				sendActionAck(msg, false, messageID, originAddress);
			} else {
				for(InetSocketAddress addressToRemove : toRemoveTemp) {
					pendingOwnActions.remove(addressToRemove).timer.cancel();
				}
				addPendingOutsideAction(msg, messageID, originAddress);
				sendActionAck(msg, true, messageID, originAddress);
			}
			break;

		}
	}

	private void sendActionAck(Message message ,boolean valid, Integer messageID, InetSocketAddress originAddress) {
		Message outMessage = new Message();
		outMessage.put("request", MessageRequest.SyncActionResponse);
		outMessage.put("ack", (Boolean)valid);
		outMessage.put("address", new InetSocketAddress(url, port));
		outMessage.put("serverMessageID", messageID);

		SynchronizedClientSocket socket = new SynchronizedClientSocket(message, originAddress, this);
		socket.sendMessage();
	}

	/**
	 * 
	 * @param message
	 * @return true if message is already a sync message, or false if the if it was not a sync message and it was propagated.
	 */
	private boolean syncBF(Message message){
		
		for (InetSocketAddress address : battlefields) {
			if(address.equals(new InetSocketAddress(url, port))) continue;
			message.put("sync", (Boolean)true);
			SynchronizedClientSocket clientSocket;
			clientSocket = new SynchronizedClientSocket(message, address, this);
			clientSocket.sendMessage();

			//messageList.put(message, 0);
		}
		return false;
	}


	private void addPendingOutsideAction(Message message, Integer messageID, InetSocketAddress originAddress) {
		Timer timer = new Timer();
		pendingOutsideActions.put(new ActionID(messageID, originAddress), new ActionInfo(message, timer, false));
		timer.schedule(new ScheduledTask(), timeout);
	}


	public synchronized void syncActionWithBattlefields(Message message) {
		Timer timer = new Timer();

		pendingOwnActions.put(++localMessageCounter, new ActionInfo(message, timer, true));
		message.put("serverMessageID", localMessageCounter);
		sendSyncMessage(message);
		timer.schedule(new ScheduledTask(), timeout);
	}

	private void sendSyncMessage(Message message){
		SynchronizedClientSocket clientSocket;
		message.put("sync", (Boolean)true);
		message.put("serverAddress", new InetSocketAddress(url, port));
		message.put("serverMessageID", localMessageCounter);

		for (InetSocketAddress address : battlefields) {
			if(address.equals(new InetSocketAddress(url, port))) continue;
			clientSocket = new SynchronizedClientSocket(message, address, this);
			clientSocket.sendMessage();
		}
	}




	public InetSocketAddress getAddress() {
		return new InetSocketAddress(url, port);
	}


	/**
	 * Close down the battlefield. Unregisters
	 * the serverSocket so the program can 
	 * actually end.
	 */ 
	public synchronized void shutdown() {
		// Remove all units from the battlefield and make them disconnect from the server
		/*for (Unit unit : units) {
			unit.disconnect();
			unit.stopRunnerThread();
		}*/

		//serverSocket.unRegister();
	}

	private class ScheduledTask extends TimerTask implements Runnable {
		private IMessageReceivedHandler handler;
		private Message message;
		private InetSocketAddress destinationAddress;

		/*
		ScheduledTask(){
		}*/

		@Override
		public void run() {
			System.out.println("TIME OUT");
			//handler.onReadExceptionThrown(message, destinationAddress);
		}
	}

	private class ActionID {
		public Integer messageId; //Action Message ID
		public InetSocketAddress address; //Server responsible for action
		public ActionID(Integer messageId, InetSocketAddress address) {
			this.messageId = messageId;
			this.address = address;

		}

		@Override
		public boolean equals(Object o) {
			return address.equals(((ActionID)o).address) && messageId.equals(((ActionID)o).messageId) ;
		}

	}

	private class ActionInfo {
		public Message message;
		public Timer timer;
		public Queue<InetSocketAddress> ackReceived;
		public ActionInfo(Message message, Timer timer, boolean activateQueue) {
			this.message = message;
			this.timer = timer;
			if(activateQueue)
				this.ackReceived = new ConcurrentLinkedQueue<InetSocketAddress>();
			else 
				this.ackReceived = null;
		}
	}
}
