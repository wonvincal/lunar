package performance.baseline2;

public class States {
	public static State IDLE = new IdleState();
	public static State ACTIVE = new ActiveState();
	public static State WAITING_FOR_SERVICES = new WaitForServicesState();
	public static State STOP = new StopState(); // child service can choose to implement its own state before this if it
										 // wants to do some asynchronous cleanup tasks before getting the thread 
										 // stopped
	public static State STOPPED = new StoppedState();
}
