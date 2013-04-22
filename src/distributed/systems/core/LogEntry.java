package distributed.systems.core;

import java.io.Serializable;
import java.net.InetSocketAddress;


public class LogEntry implements Serializable {

	private static final long serialVersionUID = 8054991011970570003L;
	private InetSocketAddress origin;
	//	private Long id; //Generic id can be JobID, ClusterID and GSID
	private LogEntryType event;
	private Position from, to;
	private int value = -1;	
	private int[] clock;
		
	public static class Position {
		int x, y;
		public Position(int x, int y) { this.x = x; this.y = y; }
		public int getX() { return x; }
		public int getY() { return y; }
	}

	/**
	 * Use this for server connects.
	 * @param event
	 * @param origin
	 * @param clock
	 */
	public LogEntry(int[] clock, LogEntryType event, InetSocketAddress origin){
		this.setOrigin(origin);
		this.setEvent(event);
		this.setClock(clock);
	}
	
	/**
	 * Use this for Spawn.
	 * @param event
	 * @param origin
	 * @param to
	 * @param clock
	 */
	public LogEntry(int[] clock, LogEntryType event, InetSocketAddress origin, Position to){
		this.setOrigin(origin);
		this.setEvent(event);
		this.setClock(clock);
		this.to = to;
	}
	
	/**
	 * Use this for Move
	 * @param event
	 * @param origin
	 * @param from
	 * @param to
	 * @param clock
	 */
	public LogEntry(int[] clock, LogEntryType event, InetSocketAddress origin, Position from, Position to){
		this.setOrigin(origin);
		this.setEvent(event);
		this.setClock(clock);
		this.to = to;
		this.from = from;
	}
	
	/**
	 * Use this for Atack/Heal.
	 * @param event
	 * @param origin
	 * @param from
	 * @param to
	 * @param amount
	 * @param clock
	 */
	public LogEntry(int[] clock, LogEntryType event, InetSocketAddress origin, Position from, Position to, int amount){
		this.setOrigin(origin);
		this.setEvent(event);
		this.setClock(clock);
		this.to = to;
		this.from = from;
		this.value = amount;
	}

	public Position getFrom() {
		return from;
	}

	public void setFrom(Position from) {
		this.from = from;
	}

	public Position getTo() {
		return to;
	}

	public void setTo(Position to) {
		this.to = to;
	}

	public int getValue() {
		return value;
	}

	public void setValue(int value) {
		this.value = value;
	}
	
	public LogEntryType getEvent() {
		return event;
	}

	public void setEvent(LogEntryType event) {
		this.event = event;
	}

	public int[] getClock() {
		return clock;
	}

	public void setClock(int[] clock) {
		this.clock = clock;
	}

	public InetSocketAddress getOrigin() {
		return origin;
	}

	public void setOrigin(InetSocketAddress origin) {
		this.origin = origin;
	}

	@Override
	public String toString(){
		String s = "";

		if(clock != null){
			
			// Clock
			s += "[";
			for(int i = 0; i< clock.length; i++) {
				if(i==clock.length-1) {
					s+= String.format("%4d",clock[i]);
				} else {
					s+= String.format("%4d,",clock[i]);
				}
			}
			s+= "]";
			
			if(event != null)
				s += " " + event.name();
			
			if(origin != null)
				s += " " + origin.getHostName()+ ":"+origin.getPort();
			
			if(from != null)
				s += " <" + from.getX()+ ","+from.getY()+">";
			
			if(to != null)
				s += " <" + to.getX()+ ","+to.getY()+">";
			
			if(value >= 0)
				s += " "+ value;

		}

		return s;

	}

}

