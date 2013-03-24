package distributed.systems.das;

/**
 * Class containing the global gamestate. This
 * state contains small things, which all threads 
 * need to know. 
 * 
 * @author Pieter Anemaet, Boaz Pat-El
 */
public class GameState {
	// Is-the-program-actually-running-flag
	private static volatile boolean running = true;
	// Relation between game time and real time
	public static final double GAME_SPEED = .01;
	// The number of players in the game
	private static int playerCount = 0;

	/**
	 * Stop the program from running. Inform all threads
	 * to close down.
	 */
	public static void haltProgram() {
		running = false;
		System.out.println("HALT PROGRAM");
	}

	/**
	 * Get the current running state 
	 * @return true if the program is supposed to 
	 * keep running.
	 */
	public static boolean getRunningState() {
		return running;
	}

	/**
	 * Get the number of players currently in the game.
	 * @return int: the number of players currently in the game.
	 */
	public static int getPlayerCount() {
		return playerCount;
	}
}
