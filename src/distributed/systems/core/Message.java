package distributed.systems.core;

import java.io.Serializable;
import java.util.HashMap;

import distributed.systems.das.units.Unit;

public class Message implements Serializable {
	
	private HashMap<String, Serializable> attributes;
	
	public Message() {
		attributes = new HashMap<String, Serializable>();
	}
		
	/**
	 * 
	 */
	private static final long serialVersionUID = 7125703119230765933L;

	public Serializable get(String key) {
		return attributes.get(key);
	}

	public void put(String key, Serializable value) {
		attributes.put(key, value);
	}
	
	public void put(String key, int value) {
		attributes.put(key, Integer.valueOf(value));
	}
	
	public void put(String key, Unit value) {
		attributes.put(key, value);
	}


}
