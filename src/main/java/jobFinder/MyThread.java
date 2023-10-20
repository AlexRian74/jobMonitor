package jobFinder;
//Custom subclass of Thread with ability to set starting
//window position
public class MyThread extends Thread{
	int xPos = 0;

	public void setPos(int xPos) {
		this.xPos = xPos;
	}
}
