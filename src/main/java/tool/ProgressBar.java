package tool;

import java.io.PrintStream;

/**
 * a class to print progress info in console. 
 * the problem is: for a long line that wraped, \r can not return to the correct position
 * 
 * @author chenzero
 */
public class ProgressBar {
	PrintStream ps;
	int lastLineLen=0;

	public ProgressBar() {
		ps = System.out;
	}
	
	public ProgressBar(PrintStream ps) {
		this.ps = ps;
	}

	public void done(String s) {
		ps.print("\r\n");
		if(s!=null) {
			ps.println(s);
		}
	}
	
    String anim= "|/-\\";
    int idx=0;
	
	public void update(int finished, int total) {
		int p = 0;
		if(total!=0) {
			p = (int)((double)finished/total * 100);
		}
		
		String info = String.format("%c (%d/%d %d%%)", anim.charAt(idx), finished, total, p);
		ps.print(info);
		int len = info.length();
		
		int cnt = this.lastLineLen - len; 
		for(int i=cnt;i>=0;i--) {
			ps.print(" ");
		}
		this.lastLineLen = len;
		ps.print("\r");
		
		idx ++;
		idx = idx % 4;
		
	}
	
	public static void main(String[] args) throws Exception {
		int stime = 1000;
		ProgressBar pb = new ProgressBar();
		for(int i=0;i<100;i++) {
			pb.update(i, 100); // step by n
			Thread.sleep(stime);
		}
		pb.done("done");
		//System.out.println("done");
	}
}
