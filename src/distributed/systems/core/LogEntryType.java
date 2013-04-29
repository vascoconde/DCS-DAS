package distributed.systems.core;

import java.io.Serializable;

/**
 * 
 * Different types of control messages. Feel free to add new message types if you need any. 
 * 
 * @author Niels Brouwers
 *
 */
public enum LogEntryType implements Serializable {

	// used by RM only
	SPAWN,
	MOVE,
	ATACK,
	HEAL,
	CONNECT_BF,
	REMOVE,
	
	// unknown log
	UNKNOWN

}
