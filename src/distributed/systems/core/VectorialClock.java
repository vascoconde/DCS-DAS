package distributed.systems.core;

import java.io.Serializable;

/**
 * Class that represents a Vectorial Clock
 */
public class VectorialClock implements Serializable {

	private static final long serialVersionUID = -4298646294358826625L;

	private Integer[] clock;

	public VectorialClock(int nEntities) {
		clock = new Integer[nEntities];
		for (int i = 0; i < nEntities; i++) {
			clock[i] = 0;
		}
	}
	
	public VectorialClock(Integer[] clock) {
		this.clock = clock;
	}

	/**
	 * Returns the clock.
	 * @return clock
	 */
	public Integer[] getClock() {
		return clock.clone();
	}

	/**
	 * Increment the clock at a specified ID by one unit
	 * @param id 
	 */
	public synchronized Integer[] incrementClock(int id) {
		clock[id]++;
		return clock.clone();
	}

	/**
	 * Updates the clock using an external clock as reference.
	 * @param externalClock
	 * @param id 
	 */
	public synchronized Integer[] updateClock(Integer[] externalClock) {
		for (int i = 0; i < clock.length; i++) {
			if (externalClock[i] > clock[i]) {
				clock[i] = externalClock[i];
			}
		}
		return clock.clone();
	}

	public synchronized Integer[] updateClockID(Integer[] externalClock, int id) {
		for (int i = 0; i < clock.length; i++) {
			if(id!=i) {
				if (externalClock[i] > clock[i]) {
					clock[i] = externalClock[i];
				}
			} else {
				clock[i] = externalClock[i];
			}
		}
		return clock.clone();
	}

	public void setClock(Integer[] externalClock) {
		for (int i = 0; i < clock.length; i++) {
			clock[i] = externalClock[i];
		}
	}

	public void setIndexValue(int id, Integer value) {
		assert(id<clock.length);
		assert(id>=0);
		clock[id] = value;
	}
 
	@Override
	public String toString(){
		String s = "[ ";
		for( int i= 0; i< this.clock.length; i++){
			s+=  this.clock[i];
			s+=", "; 
		}
		s+= "]";
		return s;}
	
	

}
