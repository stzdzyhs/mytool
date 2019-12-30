package tool;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Stack;
import java.util.concurrent.Callable;

/**
 * This is a tool to sync two dir(s)
 * the difference with other tools like rsync is: it just prints linux commands needed to sync those two dirs.
 * instead of changing any files.
 * 
 * for comparing files, if two files with same last modification time and size, they are considered same.
 * it will ignore those symbol links.
 * 
 * @author chenzero 
 * 2019-12-30
 */
public class SyncDir implements Callable<Void> {

	File ldir;
	File rdir;
	
	public void init(String ldir, String rdir) throws IOException {
		this.ldir = new File(ldir);
		if(!this.ldir.exists() || !this.ldir.isDirectory()) {
			throw new IOException("invalid left dir:" + this.ldir.getCanonicalPath());
		}

		this.rdir = new File(rdir);
		if(!this.rdir.exists() || !this.rdir.isDirectory()) {
			throw new IOException("invalid right dir:" + this.rdir.getCanonicalPath());
		}
	}
	
	/**
	 * a private class used for sorting and comparing
	 */
	static class FileInfo implements Comparable<FileInfo>  {
		public File f;
		public String name;
		public boolean processed = false;
		
		public FileInfo() {
		}
		
		public FileInfo(File f) {
			this.f = f;
			name = f.getName();
		}
		
		public FileInfo(String name) {
			this.f = null;
			this.name = name;
		}
		
		@Override
		public int compareTo(FileInfo o) {
			int ret = this.name.compareTo(o.name);
			return ret;
		}

		public void setName(String name) {
			this.name = name;
		}
		
		public void setProcessed(boolean v) {
			this.processed = v;
		}

		public String toString() {
			String ret;
			try {
				ret = name + " " + f.getCanonicalPath();
			}
			catch(Exception e) {
				ret = name + "Error:" + e.getMessage();
			}
			return ret;
		}
	}
	
	private void compareDir(String rpath, File dir1, File dir2, Stack<String> stk, boolean delFlag) throws Exception {
		File[] fs1 = dir1.listFiles();
		File[] fs2 = dir2.listFiles();
		FileInfo[] fss2 = new FileInfo[fs2.length];
		for(int i=0;i<fs2.length;i++) {
			fss2[i] = new FileInfo(fs2[i]);
		}
		
		// for using binary search in the right file list.
		Arrays.sort(fss2);
		String name;
		int idx;
		
		FileInfo kf = new FileInfo();
		
		for(int i=fs1.length-1;i>=0;i--) {
			// skip symbol link
			if (Files.isSymbolicLink(fs1[i].toPath())) {
				System.err.println("# Skip symbol link: " + fs1[i].getCanonicalPath());
				continue;
			}
			
			name = fs1[i].getName();
			
			kf.setName(name);
			idx = Arrays.binarySearch(fss2, kf);
			if(idx<0) { // not found
				if(fs1[i].isFile()) {
					System.out.printf("cp --preserve=timestamps '%s' '%s'\n", fs1[i].getCanonicalPath(), dir2.getCanonicalPath() );
				}
				else if(fs1[i].isDirectory()) {
					System.out.printf("cp --preserve=timestamps -R '%s' '%s'\n", fs1[i].getCanonicalPath(), dir2.getCanonicalPath() );
				}
				else {
					System.out.printf("# Skip other file: %s \n", fs1[i].getCanonicalPath());
				}
			}
			else {
				fss2[idx].setProcessed(true);
				if(fs1[i].isFile()) {
					if(fss2[idx].f.isFile()) {
						long mt1 = fs1[i].lastModified();
						long mt2 = fss2[idx].f.lastModified();
						
						if(mt1==mt2) {
							long sz1 = fs1[i].length();
							long sz2 = fss2[idx].f.length();

							if(sz1==sz2) {
								// same file, skip copy
							}
							else {
								System.out.printf("# WARNING: found two files with same timestamp but in diff size\n");
								System.out.printf("/bin/cp --preserve=timestamps -f '%s' '%s'\n", fs1[i].getCanonicalPath(), dir2.getCanonicalPath() );
							}
						}
						else if(mt1<mt2) {
							System.out.printf("# WARNING: overwrite a newer file \n");
							System.out.printf("/bin/cp --preserve=timestamps -f '%s' '%s'\n", fs1[i].getCanonicalPath(), dir2.getCanonicalPath() );
						}
						else { // mt1 > mt2,
							System.out.printf("/bin/cp --preserve=timestamps -f '%s' '%s'\n", fs1[i].getCanonicalPath(), dir2.getCanonicalPath() );
						}
					}
					else if(fss2[idx].f.isDirectory()) {
						if(delFlag) {
							System.out.printf("rm -Rf '%s'\n", fss2[idx].f.getCanonicalPath() );
							System.out.printf("/bin/cp --preserve=timestamps '%s' '%s'\n", fs1[i].getCanonicalPath(), dir2.getCanonicalPath() );
						}
						else {
							System.err.printf("# ERROR: can not remove dir in right, left: %s right: %s \n", fs1[i].getCanonicalPath(), fss2[idx].f.getCanonicalPath() );
							throw new IOException("can not delete dir");
						}
					}
					else {
						if(delFlag) {
							System.out.printf("# WARNING: remove a special file in right dir \n");
							System.out.printf("rm -f '%s'\n", fss2[idx].f.getCanonicalPath() );
							System.out.printf("/bin/cp --preserve=timestamps '%s' '%s'\n", fs1[i].getCanonicalPath(), dir2.getCanonicalPath() );
						}
						else {
							System.err.printf("# ERROR: can not remove special file in right dir: %s \n", fss2[idx].f.getCanonicalPath() );
							throw new IOException("can not delete dir");
						}
					}
				}
				else if(fs1[i].isDirectory()) {
					if(fss2[idx].f.isDirectory()) {
						stk.push(rpath + "/" + fss2[idx].name);
					}
					else { // not care if fss2[idx].f is file or other special file  
						if(delFlag) {
							System.out.printf("rm -f '%s'\n", fss2[idx].f.getCanonicalPath() );
							System.out.printf("/bin/cp --preserve=timestamps -R '%s' '%s'\n", fs1[i].getCanonicalPath(), dir2.getCanonicalPath() );
						}
						else {
							System.err.printf("# ERROR: can not remove file in right dir: %s \n", fss2[idx].f.getCanonicalPath() );
							throw new IOException("can not remove file: " + fss2[idx].f.getCanonicalPath());
						}
					}
				}
				else {
					System.out.printf("# Skip special file: %s \n", fs1[i].getCanonicalPath());
				}
			}
		} // end for
		
		if(delFlag) {
			for(FileInfo fi: fss2) {
				if(!fi.processed) {
					if(fi.f.isFile()) {
						System.out.printf("rm -f '%s'\n", fi.f.getCanonicalPath() );
					}
					else if(fi.f.isDirectory()) {
						System.out.printf("rm -Rf '%s'\n", fi.f.getCanonicalPath() );
					}
					else {
						System.out.printf("WARNING: delete special file \n" );
						System.out.printf("rm -f '%s'\n", fi.f.getCanonicalPath() );
					}
				}
			}
				
		}
	}
	
	public Void call() throws Exception {
		Stack<String> stk = new Stack<String>();

		final String lpath = this.ldir.getCanonicalPath();
		final String rpath = this.rdir.getCanonicalPath();
		
		stk.add("");
		
		String relPath;
		while (stk.size() > 0) {
			relPath = stk.remove(0);
			
			File d1 = new File(lpath + relPath);
			File d2 = new File(rpath + relPath);
			
			// TODO: the delFlag is alway true
			compareDir(relPath, d1, d2, stk, true);
		}
		return null;
	}
	
	public static void main(String[] args) throws Exception {
		if(args.length!=2) {
			System.out.println("SyncDir leftDir rightDir");
			System.exit(1);
			return;
		}

		SyncDir p = new SyncDir();
		p.init(args[0], args[1]);
		p.call();
	}

}
