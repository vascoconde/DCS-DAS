package distributed.systems.das;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import distributed.systems.core.IMessageReceivedHandler;
import distributed.systems.core.Message;
import distributed.systems.core.SynchronizedClientSocket;
import distributed.systems.core.SynchronizedSocket;
import distributed.systems.das.units.Dragon;
import distributed.systems.das.units.Player;
import distributed.systems.das.units.Unit;
import distributed.systems.das.units.Unit.UnitType;

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

	/* The static singleton */
	//private static BattleField battlefield;

	/* Primary socket of the battlefield */ 
	//private Socket serverSocket;
	private SynchronizedSocket serverSocket;
	private String url;
	private int port;
	
	private Map<Integer, Message> messageList;


	/* The last id that was assigned to an unit. This variable is used to
	 * enforce that each unit has its own unique id.
	 */
	private int lastUnitID = 0;

	public final static String serverID = "server";
	public final static int MAP_WIDTH = 25;
	public final static int MAP_HEIGHT = 25;
	//private ArrayList <Unit> units; 
	private Map<InetSocketAddress, Integer> units; 

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
		clientSocket.sendMessageWitResponse();
	}
	
	private synchronized void initBattleField(){
		map = new Unit[MAP_WIDTH][MAP_WIDTH];
		serverSocket = new SynchronizedSocket(url, port);
		serverSocket.addMessageReceivedHandler(this);
		//units = new ArrayList<Unit>();
		units = new ConcurrentHashMap<InetSocketAddress, Integer>();
		
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
		if(unit == null) return false;
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
		Message reply = null;
		String origin = (String)msg.get("origin");
		MessageRequest request = (MessageRequest)msg.get("request");
		Unit unit;
		switch(request)
		{
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

		
			case spawnUnit: {
				System.out.println("BATTLE FIELD:Spawn" + port);
				System.out.println(battlefields.toString());

				Boolean succeded = this.spawnUnit((Unit)msg.get("unit"), (Integer)msg.get("x"), (Integer)msg.get("y"));
				if(succeded) {
					units.put((InetSocketAddress)msg.get("address"), 0);	
				}
				
				reply = new Message();
				//int x = (Integer)msg.get("x");
				//int y = (Integer)msg.get("y");
				reply.put("id", msg.get("id"));
				reply.put("succeded", succeded);
				
				if(syncBF(msg)) return null;

				return reply;
			}
			case putUnit:
				this.putUnit((Unit)msg.get("unit"), (Integer)msg.get("x"), (Integer)msg.get("y"));
				
				if(syncBF(msg)) return null;

				break;
			case getUnit:
			{
				reply = new Message();
				int x = (Integer)msg.get("x");
				int y = (Integer)msg.get("y");
				/* Copy the id of the message so that the unit knows 
				 * what message the battlefield responded to. 
				 */
				reply.put("id", msg.get("id"));
				// Get the unit at the specific location
				reply.put("unit", getUnit(x, y));
				if(syncBF(msg)) return null;

				return reply;
				//break;
			}
			case getType:
			{
				reply = new Message();
				int x = (Integer)msg.get("x");
				int y = (Integer)msg.get("y");
				/* Copy the id of the message so that the unit knows 
				 * what message the battlefield responded to. 
				 */
				reply.put("id", msg.get("id"));
				if (getUnit(x, y) instanceof Player)
					reply.put("type", UnitType.player);
				else if (getUnit(x, y) instanceof Dragon)
					reply.put("type", UnitType.dragon);
				else reply.put("type", UnitType.undefined);
				if(syncBF(msg)) return null;

				return reply; 
				//break;
			}
			case dealDamage:
			{
				int x = (Integer)msg.get("x");
				int y = (Integer)msg.get("y");
				unit = this.getUnit(x, y);
				if (unit != null)
					unit.adjustHitPoints( -(Integer)msg.get("damage") );
				/* Copy the id of the message so that the unit knows 
				 * what message the battlefield responded to. 
				 */
				reply = new Message();
				reply.put("id", (Integer)msg.get("id"));
				if(syncBF(msg)) return null;

				return reply;
				//break;
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
				reply = new Message();
				reply.put("id", (Integer)msg.get("id"));
				if(syncBF(msg)) return null;

				return reply;

				//break;
			}
			case moveUnit:
				//System.out.println("BATTLEFIELD: MOVEUNIT");
				reply = new Message();
				Unit tempUnit = (Unit)msg.get("unit");
				/*
				if(temptUnit == null) {
					System.out.println("NULL");
				}*/
				
				boolean move = this.moveUnit((Unit)msg.get("unit"), (Integer)msg.get("x"), (Integer)msg.get("y"));
				/* Copy the id of the message so that the unit knows 
				 * what message the battlefield responded to. 
				 */
				reply.put("id", (Integer)msg.get("id"));
				if(move) { 
					reply.put("x", (Integer)msg.get("x"));
					reply.put("y", (Integer)msg.get("y"));
				} else {
					reply.put("x", tempUnit.getX());
					reply.put("y", tempUnit.getY());
				}
				if(syncBF(msg)) return null;


				return reply;
				//break;
			case removeUnit:
				this.removeUnit((Integer)msg.get("x"), (Integer)msg.get("y"));
				
				if(syncBF(msg)) return null;
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
	
	/**
	 * 
	 * @param message
	 * @return true if message is already a sync message, or false if the if it was not a sync message and it was propagated.
	 */
	private boolean syncBF(Message message){
		if((Boolean)message.get("sync") != null && (Boolean)message.get("sync") == true) {
			
			return true;
		}
		for (InetSocketAddress address : battlefields) {
			if(address.equals(new InetSocketAddress(url, port))) continue;
			message.put("sync", (Boolean)true);
			SynchronizedClientSocket clientSocket;
			clientSocket = new SynchronizedClientSocket(message, address, this);
			clientSocket.sendMessage();
		}
		return false;
		
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
	
}
